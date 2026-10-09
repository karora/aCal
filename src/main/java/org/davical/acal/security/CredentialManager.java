/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.davical.acal.security;

import android.content.Context;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts server passwords for storage, with an AES-GCM key that is generated
 * inside the Android Keystore and never leaves it.
 *
 * If the Keystore cannot be used then encrypt() and decrypt() fail, rather
 * than falling back to a weaker key.
 *
 * Values written by earlier versions (prefix "ENC:") can still be decrypted so
 * that they can be migrated, but nothing is encrypted that way any more.
 */
public class CredentialManager {

    private static final String TAG = "CredentialManager";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEYSTORE_ALIAS = "AcalCredentialKeyV2";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    // Prefix to identify encrypted values
    private static final String ENCRYPTED_PREFIX = "ENC2:";

    // The scheme used up to version 1.69: AES-CBC, with a key that was either
    // wrapped by an RSA key in the Keystore or, if that failed, derived from
    // the device's ANDROID_ID.
    private static final String LEGACY_PREFIX = "ENC:";
    private static final String LEGACY_KEYSTORE_ALIAS = "AcalCredentialKey";
    private static final String LEGACY_RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String LEGACY_AES_MODE = "AES/CBC/PKCS5Padding";
    private static final int LEGACY_IV_LENGTH = 16;

    private static CredentialManager instance;
    private final Context context;
    private SecretKey keystoreKey;

    private CredentialManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized CredentialManager getInstance(Context context) {
        if (instance == null) {
            instance = new CredentialManager(context);
        }
        return instance;
    }

    /**
     * Encrypts a password for storage.
     * @param plaintext The password to encrypt
     * @return Encrypted password string with prefix, or null on error
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // The Keystore insists on choosing the IV itself
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKeystoreKey());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != GCM_IV_LENGTH) {
                throw new GeneralSecurityException("Unexpected IV from the Keystore");
            }

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypts a stored password.
     * @param encrypted The encrypted password string
     * @return Decrypted password, the original if not encrypted, or null on error
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }

        if (encrypted.startsWith(LEGACY_PREFIX)) {
            return decryptLegacy(encrypted.substring(LEGACY_PREFIX.length()));
        }

        // Return as-is if not encrypted (legacy plaintext)
        if (!encrypted.startsWith(ENCRYPTED_PREFIX)) {
            return encrypted;
        }

        try {
            String data = encrypted.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.decode(data, Base64.NO_WRAP);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new GeneralSecurityException("Encrypted value is too short");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKeystoreKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, combined, 0, GCM_IV_LENGTH));
            byte[] decrypted = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    /**
     * Check if a value is encrypted, by this or an earlier version.
     */
    public boolean isEncrypted(String value) {
        return value != null && (value.startsWith(ENCRYPTED_PREFIX) || value.startsWith(LEGACY_PREFIX));
    }

    /**
     * Check if a stored value is plaintext, or was encrypted by an earlier
     * version, and so should be decrypted and encrypted again.
     */
    public boolean needsReEncryption(String value) {
        return value != null && !value.isEmpty() && !value.startsWith(ENCRYPTED_PREFIX);
    }

    private synchronized SecretKey getKeystoreKey() throws GeneralSecurityException {
        if (keystoreKey != null) {
            return keystoreKey;
        }

        KeyStore keyStore = loadKeystore();
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keystoreKey = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        } else {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            // No user authentication requirement: sync has to be able to log in
            // to the server while the device is locked.
            generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            keystoreKey = generator.generateKey();
        }
        if (keystoreKey == null) {
            throw new GeneralSecurityException("Keystore key for credentials is missing");
        }
        return keystoreKey;
    }

    private static KeyStore loadKeystore() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not load the Android Keystore", e);
        }
        return keyStore;
    }

    /**
     * Decrypts a value written by version 1.69 or earlier. That version could
     * have used either of two keys and did not record which, so try both.
     */
    private String decryptLegacy(String data) {
        byte[] combined;
        try {
            combined = Base64.decode(data, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Stored password is not valid Base64", e);
            return null;
        }
        if (combined.length <= LEGACY_IV_LENGTH) {
            Log.e(TAG, "Stored password is too short to decrypt");
            return null;
        }

        for (SecretKey key : legacyKeys()) {
            try {
                Cipher cipher = Cipher.getInstance(LEGACY_AES_MODE);
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(combined, 0, LEGACY_IV_LENGTH));
                byte[] decrypted = cipher.doFinal(combined, LEGACY_IV_LENGTH, combined.length - LEGACY_IV_LENGTH);

                // CBC has no integrity check, so the wrong key can occasionally
                // produce validly padded rubbish. Rejecting malformed UTF-8
                // catches nearly all of those.
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(decrypted)).toString();
            } catch (GeneralSecurityException | CharacterCodingException e) {
                // Wrong key: try the next one
            }
        }
        Log.e(TAG, "Could not decrypt a password stored by an earlier version");
        return null;
    }

    private List<SecretKey> legacyKeys() {
        List<SecretKey> keys = new ArrayList<>(2);
        try {
            String wrapped = context.getSharedPreferences("acal_security", Context.MODE_PRIVATE)
                    .getString("aes_key", null);
            if (wrapped != null) {
                keys.add(new SecretKeySpec(legacyDecryptWithRsa(wrapped), "AES"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not unwrap the earlier credential key", e);
        }
        try {
            keys.add(legacyDeviceDerivedKey());
        } catch (Exception e) {
            Log.w(TAG, "Could not derive the earlier fallback credential key", e);
        }
        return keys;
    }

    private byte[] legacyDecryptWithRsa(String encrypted) throws Exception {
        KeyStore.Entry entry = loadKeystore().getEntry(LEGACY_KEYSTORE_ALIAS, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new GeneralSecurityException("Earlier Keystore key is missing");
        }

        Cipher cipher = Cipher.getInstance(LEGACY_RSA_MODE);
        cipher.init(Cipher.DECRYPT_MODE, ((KeyStore.PrivateKeyEntry) entry).getPrivateKey());

        byte[] encryptedBytes = Base64.decode(encrypted, Base64.NO_WRAP);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(encryptedBytes);
        CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int len;
        while ((len = cipherInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        cipherInputStream.close();

        return outputStream.toByteArray();
    }

    /**
     * The key earlier versions fell back to when the Keystore failed. It can be
     * recomputed by anything able to read this app's data, which is why it is
     * now only ever used to read old values so they can be re-encrypted.
     */
    private SecretKey legacyDeviceDerivedKey() throws GeneralSecurityException {
        String androidId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String packageName = context.getPackageName();
        String seed = androidId + packageName + "aCal-Salt-2024";

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));

        // Use first 16 bytes for AES-128
        byte[] keyBytes = new byte[16];
        System.arraycopy(hash, 0, keyBytes, 0, 16);

        return new SecretKeySpec(keyBytes, "AES");
    }
}
