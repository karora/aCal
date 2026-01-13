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

package com.morphoss.acal.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * Manages secure credential storage using Android Keystore (API 18+)
 * or a device-derived fallback for older devices.
 */
public class CredentialManager {

    private static final String TAG = "CredentialManager";
    private static final String KEYSTORE_ALIAS = "AcalCredentialKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String AES_MODE = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    // Prefix to identify encrypted values
    private static final String ENCRYPTED_PREFIX = "ENC:";

    private static CredentialManager instance;
    private final Context context;
    private SecretKey aesKey;

    private CredentialManager(Context context) {
        this.context = context.getApplicationContext();
        initializeKey();
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
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));

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
     * @return Decrypted password, or the original if not encrypted
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }

        // Return as-is if not encrypted (legacy plaintext)
        if (!encrypted.startsWith(ENCRYPTED_PREFIX)) {
            return encrypted;
        }

        try {
            String data = encrypted.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.decode(data, Base64.NO_WRAP);

            // Extract IV and encrypted data
            byte[] iv = new byte[IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    /**
     * Check if a value is encrypted.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private void initializeKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // API 18+: Use Android Keystore
            initializeKeystore();
        } else {
            // API 14-17: Use device-derived key
            initializeFallbackKey();
        }
    }

    private void initializeKeystore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                generateKeystoreKey();
            }

            // For API 18-22, we use RSA to wrap an AES key
            // The AES key is stored encrypted in SharedPreferences
            aesKey = getOrCreateAesKey();

        } catch (Exception e) {
            Log.e(TAG, "Keystore initialization failed, using fallback", e);
            initializeFallbackKey();
        }
    }

    private void generateKeystoreKey() {
        try {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 25);

            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(KEYSTORE_ALIAS)
                    .setSubject(new X500Principal("CN=aCal Credential Key"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", KEYSTORE_PROVIDER);
            generator.initialize(spec);
            generator.generateKeyPair();

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate keystore key", e);
        }
    }

    private SecretKey getOrCreateAesKey() {
        try {
            String encryptedAesKey = context.getSharedPreferences("acal_security", Context.MODE_PRIVATE)
                    .getString("aes_key", null);

            if (encryptedAesKey == null) {
                // Generate new AES key and encrypt it with RSA
                byte[] aesKeyBytes = new byte[16];
                new SecureRandom().nextBytes(aesKeyBytes);
                SecretKey newKey = new SecretKeySpec(aesKeyBytes, "AES");

                // Encrypt and store
                String encrypted = encryptWithRsa(aesKeyBytes);
                context.getSharedPreferences("acal_security", Context.MODE_PRIVATE)
                        .edit()
                        .putString("aes_key", encrypted)
                        .apply();

                return newKey;
            } else {
                // Decrypt existing AES key
                byte[] decrypted = decryptWithRsa(encryptedAesKey);
                return new SecretKeySpec(decrypted, "AES");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get/create AES key", e);
            initializeFallbackKey();
            return aesKey;
        }
    }

    private String encryptWithRsa(byte[] data) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);

        Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, entry.getCertificate().getPublicKey());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
        cipherOutputStream.write(data);
        cipherOutputStream.close();

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private byte[] decryptWithRsa(String encrypted) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);

        Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.DECRYPT_MODE, entry.getPrivateKey());

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

    private void initializeFallbackKey() {
        try {
            // Derive a key from device-specific values
            // This is less secure than Keystore but better than plaintext
            String androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            String packageName = context.getPackageName();
            String seed = androidId + packageName + "aCal-Salt-2024";

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes("UTF-8"));

            // Use first 16 bytes for AES-128
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);

            aesKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize fallback key", e);
        }
    }
}
