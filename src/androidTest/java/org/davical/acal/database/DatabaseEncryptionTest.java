package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.Constants;
import org.davical.acal.security.DatabaseKeyManager;
import org.davical.acal.security.StoredKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * How the database is opened when the app starts: made encrypted, encrypted
 * where it was not, or replaced when it can never be read again.  Getting any
 * of this wrong leaves the user with an empty calendar.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseEncryptionTest {

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
	}

	@Test
	public void aNewDatabaseIsEncryptedAndHasTheCurrentSchema() throws Exception {
		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals("Only the account for what is kept on the phone",
					1, TestDatabase.count(db, "dav_server", null));
			assertEquals(1, TestDatabase.count(db, "dav_server", "backend_type = 1"));
			assertEquals(0, TestDatabase.count(db, "dav_resource", null));
		}
		finally {
			db.close();
		}
		assertFalse(TestDatabase.isPlainSqlite(TestDatabase.file()));
		assertNotNull(DatabaseKeyManager.getExistingKey(TestDatabase.context()));
	}

	@Test
	public void whatIsWrittenIsThereAfterARestart() throws Exception {
		SQLiteDatabase db = TestDatabase.open();
		TestDatabase.addServer(db, "work");
		db.close();

		TestDatabase.asIfRestarted();

		db = TestDatabase.open();
		try {
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void anUnencryptedDatabaseIsEncryptedWithEverythingStillInIt() throws Exception {
		SQLiteDatabase plain = TestDatabase.createPlain();
		TestDatabase.addServer(plain, "work");
		TestDatabase.addServer(plain, "home");
		plain.execSQL("INSERT INTO dav_collection (server_id, collection_path, displayname)"
				+ " SELECT _id, '/work/calendar/', 'Work' FROM dav_server WHERE friendly_name = 'work'");
		plain.execSQL("INSERT INTO dav_resource (collection_id, name, data)"
				+ " SELECT _id, 'one.ics', 'BEGIN:VCALENDAR' FROM dav_collection");
		plain.close();
		assertTrue(TestDatabase.isPlainSqlite(TestDatabase.file()));

		TestDatabase.asIfRestarted();

		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'home'"));
			assertEquals(1, TestDatabase.count(db, "dav_collection", "displayname = 'Work'"));
			assertEquals(1, TestDatabase.count(db, "dav_resource", "name = 'one.ics' AND data = 'BEGIN:VCALENDAR'"));
		}
		finally {
			db.close();
		}
		assertFalse("The database must not be left unencrypted", TestDatabase.isPlainSqlite(TestDatabase.file()));
		assertFalse("The copy it was built in must be gone", TestDatabase.encryptingFile().exists());
	}

	@Test
	public void anEncryptedDatabaseIsNotEncryptedAgain() throws Exception {
		TestDatabase.open().close();
		byte[] key = DatabaseKeyManager.getExistingKey(TestDatabase.context());

		TestDatabase.asIfRestarted();
		TestDatabase.open().close();

		assertEquals(new String(key), new String(DatabaseKeyManager.getExistingKey(TestDatabase.context())));
	}

	@Test
	public void filesLeftByAnInterruptedRunAreRemoved() throws Exception {
		TestDatabase.open().close();
		write(TestDatabase.encryptingFile(), "half an encrypted copy");
		write(TestDatabase.plainCopyFile(), "an unencrypted copy nobody collected");

		TestDatabase.asIfRestarted();
		TestDatabase.open().close();

		assertFalse(TestDatabase.encryptingFile().exists());
		assertFalse("An unencrypted copy must not be left lying around", TestDatabase.plainCopyFile().exists());
	}

	@Test
	public void aDatabaseWhoseKeyIsLostIsReplacedAndTheUserIsTold() throws Exception {
		allowNotifications();
		SQLiteDatabase db = TestDatabase.open();
		TestDatabase.addServer(db, "work");
		db.close();

		StoredKey.loseWrappingKey();
		TestDatabase.asIfRestarted();

		db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals("Nothing of the old database can be read", 0,
					TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
			assertEquals(1, TestDatabase.count(db, "dav_server", "backend_type = 1"));
		}
		finally {
			db.close();
		}
		assertFalse(TestDatabase.isPlainSqlite(TestDatabase.file()));
		assertTrue("The user has to be told their settings are gone", isShowing(Constants.DATABASE_RESET_NOTIFICATION_ID));
	}

	@Test
	public void aFileThatTheKeyDoesNotOpenIsReplaced() throws Exception {
		TestDatabase.open().close();
		write(TestDatabase.file(), "This is not an encrypted database, nor a plain one.  It is long enough to have a header.");

		TestDatabase.asIfRestarted();

		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals(1, TestDatabase.count(db, "dav_server", null));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void theCopyForTheUserToSaveIsUnencryptedAndComplete() throws Exception {
		SQLiteDatabase db = TestDatabase.open();
		TestDatabase.addServer(db, "work");
		db.close();

		File copy = AcalDBHelper.exportPlainCopy(TestDatabase.context());

		assertTrue(TestDatabase.isPlainSqlite(copy));
		SQLiteDatabase plain = TestDatabase.openPlain(copy);
		try {
			assertEquals(AcalDBHelper.DB_VERSION, plain.getVersion());
			assertEquals(1, TestDatabase.count(plain, "dav_server", "friendly_name = 'work'"));
			assertEquals(1, TestDatabase.count(plain, "dav_server", "backend_type = 1"));
		}
		finally {
			plain.close();
		}
		assertFalse("The database itself stays encrypted", TestDatabase.isPlainSqlite(TestDatabase.file()));

		AcalDBHelper.deletePlainCopy(TestDatabase.context());
		assertFalse(copy.exists());
	}

	private static void write( File file, String content ) throws Exception {
		file.getParentFile().mkdirs();
		try ( FileOutputStream out = new FileOutputStream(file) ) {
			out.write(content.getBytes("US-ASCII"));
		}
	}

	private static void allowNotifications() {
		InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
				TestDatabase.context().getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
	}

	private static boolean isShowing( int notificationId ) throws InterruptedException {
		NotificationManager nm = (NotificationManager) TestDatabase.context().getSystemService(Context.NOTIFICATION_SERVICE);
		// The system takes a moment to show what it has been given
		for( int tries = 0; tries < 50; tries++ ) {
			for( StatusBarNotification showing : nm.getActiveNotifications() ) {
				if ( showing.getId() == notificationId ) return true;
			}
			Thread.sleep(100);
		}
		return false;
	}
}
