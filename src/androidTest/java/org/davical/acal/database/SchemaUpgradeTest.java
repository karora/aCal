package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.security.CredentialManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A database made by each earlier version of aCal comes through the upgrade
 * with the schema a new database has, and with what the user cannot get back
 * from a server still in it.
 */
@RunWith(Parameterized.class)
public class SchemaUpgradeTest {

	private static final String EVENT = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:one@example.net\n"
			+ "DTSTART:20260101T100000Z\nSUMMARY:One\nEND:VEVENT\nEND:VCALENDAR\n";

	/** Up to this version the upgrade empties what was fetched, to fetch it again. */
	private static final int LAST_VERSION_TO_REFETCH_RESOURCES = 16;
	/** Up to this version the upgrade makes the table of unsent changes again. */
	private static final int LAST_VERSION_TO_DROP_PENDING = 17;
	/** From this version passwords were already stored encrypted as they are now. */
	private static final int FIRST_VERSION_WITH_CURRENT_PASSWORDS = 23;

	@Parameters(name = "v{0}")
	public static List<Object[]> versions() throws Exception {
		List<Object[]> versions = new ArrayList<Object[]>();
		for( int version : Schema.versions() ) {
			if ( version < AcalDBHelper.DB_VERSION ) versions.add(new Object[] { version });
		}
		return versions;
	}

	private final int version;

	public SchemaUpgradeTest( int version ) {
		this.version = version;
	}

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
	}

	@Test
	public void upgradesToTheCurrentSchemaWithItsDataIntact() throws Exception {
		SQLiteDatabase old = Schema.createPlain(TestDatabase.file(), version);
		String password = version >= FIRST_VERSION_WITH_CURRENT_PASSWORDS
				? CredentialManager.getInstance(TestDatabase.context()).encrypt("secret") : "secret";
		insert(old, "dav_server", "friendly_name", "work", "hostname", "work.example.net", "use_ssl", 1,
				"port", 443, "principal_path", "/principals/someone/", "username", "someone",
				"password", password, "active", 1);
		insert(old, "dav_path_set", "server_id", 1, "set_type", 1, "path", "/calendars/someone/");
		insert(old, "dav_collection", "server_id", 1, "collection_path", "/calendars/someone/work/",
				"displayname", "Work", "holds_events", 1, "active_events", 1, "colour", "#123456");
		insert(old, "dav_resource", "collection_id", 1, "name", "one.ics", "etag", "\"etag-1\"",
				"data", EVENT, "effective_type", "VEVENT");
		boolean hadPending = insert(old, "pending_change", "collection_id", 1, "resource_id", 1,
				"old_data", EVENT, "new_data", EVENT.replace("SUMMARY:One", "SUMMARY:Changed"), "uid", "one@example.net");
		old.execSQL("UPDATE event_cache_meta SET closed = 1");
		old.close();

		TestDatabase.asIfRestarted();
		SQLiteDatabase db = TestDatabase.open();
		try {
			assertEquals(AcalDBHelper.DB_VERSION, db.getVersion());
			assertEquals("The upgraded database must have the schema of a new one", schemaOfANewDatabase(), Schema.describe(db));

			assertEquals("The server's settings exist nowhere else", 1, TestDatabase.count(db, "dav_server",
					"friendly_name = 'work' AND hostname = 'work.example.net' AND username = 'someone'"
					+ " AND port = 443 AND active = 1 AND backend_type = 0"));
			assertEquals("secret", CredentialManager.getInstance(TestDatabase.context()).decrypt(
					string(db, "SELECT password FROM dav_server WHERE friendly_name = 'work'")));
			assertFalse("A password must not be left as it was typed",
					"secret".equals(string(db, "SELECT password FROM dav_server WHERE friendly_name = 'work'")));
			assertEquals("There is one account for what is kept on the phone", 1,
					TestDatabase.count(db, "dav_server", "backend_type = 1"));
			assertEquals(2, TestDatabase.count(db, "dav_server", null));

			assertEquals(1, TestDatabase.count(db, "dav_collection",
					"collection_path = '/calendars/someone/work/' AND displayname = 'Work' AND colour = '#123456'"));

			if ( version > LAST_VERSION_TO_REFETCH_RESOURCES ) {
				assertEquals(1, TestDatabase.count(db, "dav_resource", "name = 'one.ics' AND etag = '\"etag-1\"'"));
				assertEquals(EVENT, string(db, "SELECT data FROM dav_resource WHERE name = 'one.ics'"));
			}
			if ( hadPending && version > LAST_VERSION_TO_DROP_PENDING ) {
				assertEquals("A change not yet sent exists nowhere else", 1, TestDatabase.count(db, "pending_change",
						"resource_id = 1 AND uid = 'one@example.net' AND new_data LIKE '%SUMMARY:Changed%' AND state = 0"));
			}
			assertEquals("The cache may hold events that have gone, so it is to be made again", 1,
					TestDatabase.count(db, "event_cache_meta", "closed = 0"));
			assertEquals(1, TestDatabase.count(db, "event_cache_meta", null));
		}
		finally {
			db.close();
		}
		assertFalse(TestDatabase.isPlainSqlite(TestDatabase.file()));
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

	/**
	 * Add a row with those of the values that this version has a column for.
	 * @return false if nothing was added because this version has no such table
	 */
	private static boolean insert( SQLiteDatabase db, String table, Object... namesAndValues ) {
		TreeSet<String> columns = Schema.columns(db, table);
		if ( columns.isEmpty() ) return false;
		ContentValues values = new ContentValues();
		for( int i = 0; i < namesAndValues.length; i += 2 ) {
			String column = (String) namesAndValues[i];
			if ( !columns.contains(column) ) continue;
			Object value = namesAndValues[i + 1];
			if ( value instanceof Integer ) values.put(column, (Integer) value);
			else values.put(column, (String) value);
		}
		assertTrue(db.insert(table, null, values) > 0);
		return true;
	}

	private static String string( SQLiteDatabase db, String sql ) {
		Cursor c = db.rawQuery(sql, (String[]) null);
		try {
			return c.moveToFirst() ? c.getString(0) : null;
		}
		finally {
			c.close();
		}
	}
}
