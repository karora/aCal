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
package org.davical.acal.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.davical.acal.database.AcalDBHelper;
import org.davical.acal.providers.Servers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

@RunWith(RobolectricTestRunner.class)
public class BackendTypeTest {

	/** dav_server as version 23 of the database created it. */
	private static final String DAV_SERVER_V23_SQL =
			"CREATE TABLE dav_server ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",friendly_name TEXT"
				+",supplied_user_url TEXT"
				+",supplied_path TEXT"
				+",use_ssl BOOLEAN"
				+",hostname TEXT"
				+",port INTEGER"
				+",principal_path TEXT"
				+",auth_type INTEGER"
				+",username TEXT"
				+",password TEXT"
				+",has_srv BOOLEAN"
				+",has_wellknown BOOLEAN"
				+",has_caldav BOOLEAN"
				+",has_multiget BOOLEAN"
				+",has_sync BOOLEAN"
				+",active BOOLEAN"
				+",last_checked DATETIME"
				+",use_advanced BOOLEAN"
				+",prepared_config TEXT"
				+",UNIQUE(use_ssl,hostname,port,principal_path,username)"
			+")";

	/**
	 * The numbers are what is stored, so they must never change.
	 */
	@Test
	public void theStoredNumbersStayAsTheyAre() {
		assertEquals(0, BackendType.CALDAV.value);
		assertEquals(BackendType.CALDAV, BackendType.fromValue(0));
		assertEquals(1, BackendType.LOCAL.value);
		assertEquals(BackendType.LOCAL, BackendType.fromValue(1));
	}

	@Test
	public void everyTypeReadsBackFromItsOwnNumber() {
		for ( BackendType type : BackendType.values() ) {
			assertEquals(type, BackendType.fromValue(type.value));
		}
	}

	@Test
	public void aRowFromBeforeTheColumnExistedIsCalDav() {
		assertEquals(BackendType.CALDAV, BackendType.fromValue(null));
		assertEquals(BackendType.CALDAV, BackendType.fromServerRow(new ContentValues()));
	}

	/** Not CalDAV: we must not send another kind of account's changes to a server. */
	@Test
	public void aNumberWeDoNotKnowIsNoType() {
		assertNull(BackendType.fromValue(9999));

		ContentValues serverData = new ContentValues();
		serverData.put(Servers.BACKEND_TYPE, 9999);
		assertNull(BackendType.fromServerRow(serverData));
		assertNull(StorageBackends.forServerRow(serverData));
	}

	@Test
	public void aCalDavAccountGetsTheCalDavBackend() {
		ContentValues serverData = new ContentValues();
		serverData.put(Servers.BACKEND_TYPE, BackendType.CALDAV.value);

		assertTrue(StorageBackends.forServerRow(serverData) instanceof CalDavBackend);
		assertTrue(StorageBackends.forServerRow(new ContentValues()) instanceof CalDavBackend);
		assertNull(StorageBackends.forServerRow(null));
	}

	@Test
	public void aLocalAccountGetsTheLocalBackend() {
		ContentValues serverData = new ContentValues();
		serverData.put(Servers.BACKEND_TYPE, BackendType.LOCAL.value);

		assertTrue(StorageBackends.forServerRow(serverData) instanceof LocalBackend);
	}

	@Test
	public void theUpgradeToVersion27AddsTheAccountForThisPhoneOnce() {
		SQLiteDatabase db = SQLiteDatabase.create(null);
		try {
			db.execSQL(AcalDBHelper.DAV_SERVER_TABLE_SQL);
			db.execSQL("INSERT INTO dav_server (friendly_name, hostname, active) VALUES ('Home', 'dav.example.net', 1)");

			db.execSQL(AcalDBHelper.ENSURE_LOCAL_ACCOUNT_SQL);
			db.execSQL(AcalDBHelper.ENSURE_LOCAL_ACCOUNT_SQL);

			Cursor c = db.rawQuery("SELECT friendly_name, active, backend_type FROM dav_server ORDER BY _id", null);
			try {
				assertEquals(2, c.getCount());
				c.moveToFirst();
				assertEquals("Home|1|0", c.getString(0) + "|" + c.getInt(1) + "|" + c.getInt(2));
				c.moveToNext();
				assertEquals(LocalCalendars.ACCOUNT_NAME + "|1|1", c.getString(0) + "|" + c.getInt(1) + "|" + c.getInt(2));
			}
			finally {
				c.close();
			}
		}
		finally {
			db.close();
		}
	}

	@Test
	public void theUpgradeToVersion24MakesExistingServersCalDav() {
		SQLiteDatabase db = SQLiteDatabase.create(null);
		try {
			db.execSQL(DAV_SERVER_V23_SQL);
			db.execSQL("INSERT INTO dav_server (friendly_name, hostname, active) VALUES ('Home', 'dav.example.net', 1)");

			db.execSQL(AcalDBHelper.ADD_SERVER_BACKEND_TYPE_SQL);

			ContentValues serverData = onlyServerRow(db);
			assertEquals("Home", serverData.getAsString(Servers.FRIENDLY_NAME));
			assertEquals(Integer.valueOf(0), serverData.getAsInteger(Servers.BACKEND_TYPE));
			assertEquals(BackendType.CALDAV, BackendType.fromServerRow(serverData));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void aServerAddedToANewDatabaseIsCalDavUnlessItSaysOtherwise() {
		SQLiteDatabase db = SQLiteDatabase.create(null);
		try {
			db.execSQL(AcalDBHelper.DAV_SERVER_TABLE_SQL);
			db.execSQL("INSERT INTO dav_server (friendly_name, hostname, active) VALUES ('Home', 'dav.example.net', 1)");

			assertEquals(BackendType.CALDAV, BackendType.fromServerRow(onlyServerRow(db)));
		}
		finally {
			db.close();
		}
	}

	private static ContentValues onlyServerRow(SQLiteDatabase db) {
		Cursor c = db.rawQuery("SELECT * FROM dav_server", null);
		try {
			assertEquals(1, c.getCount());
			c.moveToFirst();
			ContentValues serverData = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(c, serverData);
			return serverData;
		}
		finally {
			c.close();
		}
	}
}
