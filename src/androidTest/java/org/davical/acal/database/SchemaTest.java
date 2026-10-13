package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The schema of a new database is written down in assets/schema, one file to
 * a version, and a change to it has to come with a new version and its file.
 * That file is what the next upgrade is tested from: see the README there.
 */
@RunWith(AndroidJUnit4.class)
public class SchemaTest {

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
	}

	@Test
	public void theCurrentVersionHasItsSchemaWrittenDown() throws Exception {
		assertTrue("There is no src/androidTest/assets/schema/v" + AcalDBHelper.DB_VERSION + ".sql",
				Schema.versions().contains(AcalDBHelper.DB_VERSION));
	}

	@Test
	public void aNewDatabaseHasTheSchemaWrittenDownForThisVersion() throws Exception {
		File file = new File(TestDatabase.context().getCacheDir(), "written-down.db");
		file.delete();
		SQLiteDatabase writtenDown = Schema.createPlain(file, AcalDBHelper.DB_VERSION);
		String expected = Schema.describe(writtenDown);
		writtenDown.close();
		file.delete();

		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals("The schema has changed without a new DB_VERSION and its file in src/androidTest/assets/schema",
					expected, Schema.describe(db));
		}
		finally {
			db.close();
		}
	}
}
