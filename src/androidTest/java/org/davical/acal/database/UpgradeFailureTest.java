package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A database that cannot be brought up to the current version is left as it
 * was, at the version it had, so that nothing in it is lost and a later
 * release can upgrade it.  It must never be marked as current.
 */
@RunWith(AndroidJUnit4.class)
public class UpgradeFailureTest {

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
	}

	private static void openMustFail() {
		try {
			TestDatabase.open().close();
			fail("A database that could not be upgraded was opened as if it had been");
		}
		catch( AcalDBHelper.UpgradeFailedException expected ) {
		}
	}

	@Test
	public void aStepThatFailsLeavesTheDatabaseAsItWas() throws Exception {
		SQLiteDatabase old = Schema.createPlain(TestDatabase.file(), 25);
		TestDatabase.addServer(old, "work");
		old.execSQL("INSERT INTO dav_collection (server_id, collection_path, displayname) VALUES (1, '/work/', 'Work')");
		old.execSQL("INSERT INTO dav_resource (collection_id, name, data) VALUES (1, 'one.ics', 'BEGIN:VCALENDAR')");
		old.execSQL("INSERT INTO pending_change (collection_id, resource_id, new_data, uid) VALUES (1, 1, 'changed', 'one')");
		// The step from 25 adds this column, and cannot when it is already there
		old.execSQL("ALTER TABLE pending_change ADD COLUMN state INTEGER NOT NULL DEFAULT 0");
		old.close();
		TestDatabase.asIfRestarted();

		openMustFail();

		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			assertEquals("It must not be marked as upgraded", 25, db.getVersion());
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
			assertEquals(1, TestDatabase.count(db, "dav_resource", "name = 'one.ics'"));
			assertEquals("A change not yet sent exists nowhere else", 1,
					TestDatabase.count(db, "pending_change", "new_data = 'changed'"));
			assertFalse("What the steps before the failure did is undone too",
					Schema.columns(db, "pending_change").contains("attempts"));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void itFailsTheSameWayEveryTimeAndStillLosesNothing() throws Exception {
		SQLiteDatabase old = Schema.createPlain(TestDatabase.file(), 25);
		TestDatabase.addServer(old, "work");
		old.execSQL("ALTER TABLE pending_change ADD COLUMN state INTEGER NOT NULL DEFAULT 0");
		old.close();
		TestDatabase.asIfRestarted();

		openMustFail();
		openMustFail();
		TestDatabase.asIfRestarted();
		openMustFail();

		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			assertEquals(25, db.getVersion());
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void aDatabaseFromANewerVersionIsRefusedAndLeftAlone() throws Exception {
		SQLiteDatabase newer = Schema.createPlain(TestDatabase.file(), AcalDBHelper.DB_VERSION);
		TestDatabase.addServer(newer, "work");
		newer.execSQL("ALTER TABLE dav_server ADD COLUMN something_new TEXT");
		newer.setVersion(AcalDBHelper.DB_VERSION + 1);
		newer.close();
		TestDatabase.asIfRestarted();

		openMustFail();

		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			assertEquals(AcalDBHelper.DB_VERSION + 1, db.getVersion());
			assertEquals(1, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void aDatabaseTooOldToHaveBeenMadeByThisAppIsReplacedWithANewOne() throws Exception {
		SQLiteDatabase ancient = SQLiteDatabase.openDatabase(TestDatabase.file().getPath(), null,
				SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		ancient.execSQL("CREATE TABLE dav_server (_id INTEGER PRIMARY KEY AUTOINCREMENT, friendly_name TEXT, hostname TEXT)");
		ancient.execSQL("CREATE TABLE something_long_gone (_id INTEGER PRIMARY KEY, what TEXT)");
		ancient.execSQL("INSERT INTO dav_server (friendly_name) VALUES ('work')");
		ancient.setVersion(AcalDBHelper.OLDEST_UPGRADABLE_VERSION - 1);
		ancient.close();
		TestDatabase.asIfRestarted();

		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals(schemaOfANewDatabase(), Schema.describe(db));
			assertEquals(0, TestDatabase.count(db, "dav_server", "friendly_name = 'work'"));
			assertEquals(1, TestDatabase.count(db, "dav_server", "backend_type = 1"));
		}
		finally {
			db.close();
		}
	}

	private static String schemaOfANewDatabase() throws Exception {
		File file = new File(TestDatabase.context().getCacheDir(), "new-schema.db");
		file.delete();
		SQLiteDatabase db = Schema.createPlain(file, AcalDBHelper.DB_VERSION);
		try {
			return Schema.describe(db);
		}
		finally {
			db.close();
			file.delete();
		}
	}
}
