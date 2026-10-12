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
package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * The upgrade to version 26, which keeps a record of how committing each
 * pending change has gone.
 */
@RunWith(RobolectricTestRunner.class)
public class PendingChangeProgressTest {

	/** pending_change as version 25 of the database had it. */
	private static final String PENDING_CHANGE_V25_SQL =
			"CREATE TABLE pending_change ("
		        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",collection_id INTEGER REFERENCES dav_collection(_id)"
				+",resource_id INTEGER REFERENCES dav_resource(_id)"
				+",old_data BLOB"
				+",new_data BLOB"
				+",uid TEXT"
				+",UNIQUE(collection_id,resource_id)"
			+");";

	private static String onlyRow(SQLiteDatabase db) {
		Cursor c = db.rawQuery("SELECT new_data, uid, state, attempts, last_status, last_tried FROM pending_change", null);
		try {
			assertEquals(1, c.getCount());
			c.moveToFirst();
			return c.getString(0) + "|" + c.getString(1) + "|" + c.getInt(2) + "|" + c.getInt(3)
					+ "|" + (c.isNull(4) ? "null" : c.getInt(4)) + "|" + (c.isNull(5) ? "null" : c.getLong(5));
		}
		finally {
			c.close();
		}
	}

	@Test
	public void aChangeWaitingBeforeTheUpgradeIsStillWaitingAndNeverTried() {
		SQLiteDatabase db = SQLiteDatabase.create(null);
		try {
			db.execSQL(PENDING_CHANGE_V25_SQL);
			db.execSQL("INSERT INTO pending_change (collection_id, resource_id, new_data, uid) VALUES (1, 10, 'after', 'abc')");

			for ( String sql : AcalDBHelper.PendingChangeProgress.ADD_COLUMNS_SQL ) db.execSQL(sql);

			assertEquals("after|abc|0|0|null|null", onlyRow(db));
		}
		finally {
			db.close();
		}
	}

	@Test
	public void aNewDatabaseHasTheSameColumnsAsAnUpgradedOne() {
		SQLiteDatabase created = SQLiteDatabase.create(null);
		SQLiteDatabase upgraded = SQLiteDatabase.create(null);
		try {
			created.execSQL(AcalDBHelper.PENDING_CHANGE_TABLE_SQL);
			upgraded.execSQL(PENDING_CHANGE_V25_SQL);
			for ( String sql : AcalDBHelper.PendingChangeProgress.ADD_COLUMNS_SQL ) upgraded.execSQL(sql);

			String insert = "INSERT INTO pending_change (collection_id, resource_id, new_data, uid) VALUES (1, 10, 'after', 'abc')";
			created.execSQL(insert);
			upgraded.execSQL(insert);

			assertEquals(onlyRow(upgraded), onlyRow(created));
			assertTrue(onlyRow(created), onlyRow(created).endsWith("|0|0|null|null"));
		}
		finally {
			created.close();
			upgraded.close();
		}
	}
}
