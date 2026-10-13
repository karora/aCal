package org.davical.acal.security;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import android.content.Context;

/**
 * What the tests do to the database key that the app itself never does.
 */
public final class StoredKey {

	private StoredKey() { }

	/** The key is held in memory once read; a new process has to read it again. */
	public static void forgetCached() {
		DatabaseKeyManager.forgetCachedKey();
	}

	public static File file(Context context) {
		return new File(context.getNoBackupFilesDir(), "acal_db_key");
	}

	public static void overwriteWith(Context context, String content) throws IOException {
		try ( FileOutputStream out = new FileOutputStream(file(context)) ) {
			out.write(content.getBytes(StandardCharsets.US_ASCII));
		}
	}

	/** As happens when the device's own key store is reset: what wrapped our key is gone. */
	public static void loseWrappingKey() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
		keyStore.load(null);
		keyStore.deleteEntry("AcalDatabaseKey");
	}
}
