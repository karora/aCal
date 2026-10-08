/*
 * Copyright (C) 2026 Andrew McMillan
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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

/**
 * Holds the key that encrypts the aCal database.
 *
 * The key is 32 random bytes. It is stored wrapped by an AES-GCM key that lives
 * in the Android Keystore and never leaves the device, and is handed out in
 * SQLCipher's raw key form so that SQLCipher does not run its passphrase key
 * derivation on every open.
 *
 * The preferences file that holds the wrapped key is excluded from backups (see
 * res/xml/backup_rules.xml), since it is useless without the Keystore key.
 */
public final class DatabaseKeyManager {

	/** Name of the preferences file holding the wrapped key.  Excluded from backups. */
	public static final String PREFS_NAME = "acal_db_key";

	private static final String PREF_WRAPPED_KEY = "wrapped_key";
	private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
	private static final String KEYSTORE_ALIAS = "AcalDatabaseKey";
	private static final String WRAP_TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int KEY_BYTES = 32;

	private static byte[] cachedKey;

	private DatabaseKeyManager() { }

	/**
	 * Thrown when a wrapped key is stored but can never be unwrapped again, for
	 * example because the Keystore key that wrapped it is gone.
	 */
	public static class KeyLostException extends GeneralSecurityException {
		private static final long serialVersionUID = 1L;

		public KeyLostException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * @return true if a wrapped database key has been stored.
	 */
	public static synchronized boolean hasKey(Context context) {
		return cachedKey != null || prefs(context).contains(PREF_WRAPPED_KEY);
	}

	/**
	 * Fetch the key for an existing encrypted database.
	 *
	 * @return The key in SQLCipher raw key form, or null if none has been stored.
	 * @throws KeyLostException if the stored key can never be recovered.
	 * @throws GeneralSecurityException if the Keystore failed for some other
	 * reason, which may be temporary.
	 */
	public static synchronized byte[] getExistingKey(Context context) throws GeneralSecurityException {
		if ( cachedKey != null ) return cachedKey.clone();

		String stored = prefs(context).getString(PREF_WRAPPED_KEY, null);
		if ( stored == null ) return null;

		cachedKey = toSqlCipherKey(unwrap(stored));
		return cachedKey.clone();
	}

	/**
	 * Fetch the database key, generating and storing a new one if there is none.
	 *
	 * @return The key in SQLCipher raw key form.
	 */
	public static synchronized byte[] getOrCreateKey(Context context) throws GeneralSecurityException {
		byte[] existing = getExistingKey(context);
		if ( existing != null ) return existing;

		byte[] raw = new byte[KEY_BYTES];
		new SecureRandom().nextBytes(raw);
		try {
			String wrapped = wrap(raw);
			// commit() rather than apply(): the key must be on disk before any
			// database is encrypted with it.
			if ( !prefs(context).edit().putString(PREF_WRAPPED_KEY, wrapped).commit() )
				throw new GeneralSecurityException("Could not store the wrapped database key");
			cachedKey = toSqlCipherKey(raw);
		}
		finally {
			Arrays.fill(raw, (byte) 0);
		}
		return cachedKey.clone();
	}

	/**
	 * Forget the stored key and the Keystore key that wrapped it.  Any database
	 * encrypted with it becomes permanently unreadable.
	 */
	public static synchronized void discardKey(Context context) {
		cachedKey = null;
		prefs(context).edit().remove(PREF_WRAPPED_KEY).commit();
		try {
			KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
			keyStore.load(null);
			keyStore.deleteEntry(KEYSTORE_ALIAS);
		}
		catch ( Exception e ) {
			// Nothing useful to do: a fresh wrapping key replaces it on next use.
		}
	}

	private static SharedPreferences prefs(Context context) {
		return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	private static SecretKey wrappingKey(boolean createIfMissing) throws GeneralSecurityException {
		KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
		try {
			keyStore.load(null);
		}
		catch ( java.io.IOException e ) {
			throw new GeneralSecurityException("Could not load the Android Keystore", e);
		}

		if ( keyStore.containsAlias(KEYSTORE_ALIAS) ) {
			return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
		}
		if ( !createIfMissing ) return null;

		KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
		// No user authentication requirement: sync and alarms have to be able to
		// open the database while the device is locked.
		generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
						KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build());
		return generator.generateKey();
	}

	private static String wrap(byte[] raw) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(WRAP_TRANSFORMATION);
		cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(true));
		byte[] wrapped = cipher.doFinal(raw);
		return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
				+ ":" + Base64.encodeToString(wrapped, Base64.NO_WRAP);
	}

	private static byte[] unwrap(String stored) throws GeneralSecurityException {
		String[] parts = stored.split(":");
		if ( parts.length != 2 ) throw new KeyLostException("Stored database key is malformed", null);

		SecretKey wrappingKey = wrappingKey(false);
		if ( wrappingKey == null ) throw new KeyLostException("Keystore key for the database is missing", null);

		try {
			Cipher cipher = Cipher.getInstance(WRAP_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, wrappingKey,
					new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)));
			byte[] raw = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
			if ( raw.length != KEY_BYTES ) throw new KeyLostException("Stored database key is the wrong length", null);
			return raw;
		}
		catch ( KeyPermanentlyInvalidatedException | AEADBadTagException | IllegalArgumentException e ) {
			throw new KeyLostException("Stored database key can no longer be unwrapped", e);
		}
	}

	/**
	 * SQLCipher treats a key of the form x'<64 hex digits>' as the raw key
	 * itself, rather than as a passphrase to derive a key from.
	 */
	private static byte[] toSqlCipherKey(byte[] raw) {
		final char[] hex = "0123456789abcdef".toCharArray();
		StringBuilder key = new StringBuilder(2 * raw.length + 3);
		key.append("x'");
		for ( byte b : raw ) {
			key.append(hex[(b >> 4) & 0x0f]).append(hex[b & 0x0f]);
		}
		key.append("'");
		return key.toString().getBytes(StandardCharsets.US_ASCII);
	}
}
