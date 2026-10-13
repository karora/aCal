package org.davical.acal.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.security.DatabaseKeyManager;
import org.davical.acal.security.StoredKey;

/**
 * The app's own database file, for tests to put what they need there before
 * the app opens it.
 *
 * Each test runs in a process of its own, but they share the device, so a
 * test starts by clearing away what the one before left.
 */
public final class TestDatabase {

	private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
	private static final String[] SUFFIXES = { "", "-journal", "-wal", "-shm" };

	private TestDatabase() { }

	public static Context context() {
		return ApplicationProvider.getApplicationContext();
	}

	public static File file() {
		return context().getDatabasePath(AcalDBHelper.DB_NAME + ".db");
	}

	/** Where an encrypted copy is built before it replaces a plain database. */
	public static File encryptingFile() {
		return new File(file().getParentFile(), "acal-encrypting.db");
	}

	/** Where the unencrypted copy for the user to save is written. */
	public static File plainCopyFile() {
		return new File(context().getCacheDir(), "acal-export.db");
	}

	/**
	 * No database and no key, as on a phone aCal has just been installed on.
	 */
	public static void startWithNothing() throws InterruptedException {
		waitForTheAppToStart();
		DatabaseKeyManager.discardKey(context());
		for( File each : new File[] { file(), encryptingFile(), plainCopyFile() } ) {
			for( String suffix : SUFFIXES ) {
				new File(each.getPath() + suffix).delete();
			}
		}
		asIfRestarted();
	}

	/**
	 * The app prepares the database in the background when it starts, and must
	 * have finished before a test changes anything underneath it.
	 */
	private static void waitForTheAppToStart() throws InterruptedException {
		final CountDownLatch prepared = new CountDownLatch(1);
		AcalDBHelper.whenReady(new Runnable() {
			@Override
			public void run() {
				prepared.countDown();
			}
		});
		if ( !prepared.await(60, TimeUnit.SECONDS) )
			throw new IllegalStateException("The app did not finish preparing its database");
	}

	/**
	 * How the database is to be opened is decided once in each process, and the
	 * key is read once.  This forgets both, so the next open decides again.
	 */
	public static void asIfRestarted() {
		AcalDBHelper.forgetPreparation();
		StoredKey.forgetCached();
	}

	/** Open through the app's own helper, which is what prepares the database. */
	public static SQLiteDatabase open() {
		return new AcalDBHelper(context()).getWritableDatabase();
	}

	/**
	 * A database with the current schema that is not encrypted, as every
	 * database was before aCal encrypted them.
	 */
	public static SQLiteDatabase createPlain() {
		file().getParentFile().mkdirs();
		SQLiteDatabase db = SQLiteDatabase.openDatabase(file().getPath(), null,
				SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		new AcalDBHelper(context()).onCreate(db);
		db.setVersion(AcalDBHelper.DB_VERSION);
		return db;
	}

	public static SQLiteDatabase openPlain( File file ) {
		return SQLiteDatabase.openDatabase(file.getPath(), null,
				SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}

	/** Every plain SQLite file starts with the same 16 bytes, and an encrypted one never does. */
	public static boolean isPlainSqlite( File file ) throws IOException {
		byte[] header = new byte[SQLITE_HEADER.length];
		try ( FileInputStream in = new FileInputStream(file) ) {
			if ( in.read(header) != header.length ) return false;
		}
		return Arrays.equals(header, SQLITE_HEADER);
	}

	public static void addServer( SQLiteDatabase db, String name ) {
		db.execSQL("INSERT INTO dav_server (friendly_name, hostname, username, active) VALUES (?, ?, 'someone', 1)",
				new Object[] { name, name + ".example.net" });
	}

	public static long count( SQLiteDatabase db, String table, String where ) {
		android.database.Cursor c = db.rawQuery("SELECT count(*) FROM " + table
				+ (where == null ? "" : " WHERE " + where), (String[]) null);
		try {
			c.moveToFirst();
			return c.getLong(0);
		}
		finally {
			c.close();
		}
	}
}
