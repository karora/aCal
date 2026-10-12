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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * The upgrade to version 25, which queues what the server never accepted.
 */
@RunWith(RobolectricTestRunner.class)
public class QueueUnsentResourcesTest {

	private static final int CALDAV_COLLECTION = 1;
	private static final int OTHER_KIND_OF_COLLECTION = 2;

	private SQLiteDatabase db;

	@Before
	public void setUp() {
		db = SQLiteDatabase.create(null);
		db.execSQL(AcalDBHelper.DAV_SERVER_TABLE_SQL);
		db.execSQL(AcalDBHelper.DAV_COLLECTION_TABLE_SQL);
		db.execSQL(AcalDBHelper.DAV_RESOURCE_TABLE_SQL);
		db.execSQL(AcalDBHelper.PENDING_CHANGE_TABLE_SQL);
		db.execSQL("INSERT INTO dav_server (_id, hostname) VALUES (1, 'dav.example.net')");
		db.execSQL("INSERT INTO dav_server (_id, hostname, backend_type) VALUES (2, 'elsewhere', 9999)");
		db.execSQL("INSERT INTO dav_collection (_id, server_id, collection_path) VALUES (1, 1, '/cal/')");
		db.execSQL("INSERT INTO dav_collection (_id, server_id, collection_path) VALUES (2, 2, '/other/')");
	}

	@After
	public void tearDown() {
		db.close();
	}

	private static String event(String uid, String eol) {
		return "BEGIN:VCALENDAR" + eol + "X-WR-RELCALID-UID:not-this-one" + eol
				+ "BEGIN:VEVENT" + eol + "DTSTAMP:20260827T212736Z" + eol + "UID:" + uid + eol
				+ "SUMMARY:Tooth extraction" + eol
				+ "BEGIN:VALARM" + eol + "UID:the-alarm" + eol + "END:VALARM" + eol
				+ "END:VEVENT" + eol + "END:VCALENDAR" + eol;
	}

	private void resource(int id, int collectionId, String name, String data) {
		db.execSQL("INSERT INTO dav_resource (_id, collection_id, name, data) VALUES (?, ?, ?, ?)",
				new Object[] { id, collectionId, name, data });
	}

	/** @return each pending change as "resource_id|collection_id|uid|old_data|new_data" */
	private List<String> pending() {
		List<String> rows = new ArrayList<String>();
		Cursor c = db.rawQuery("SELECT resource_id, collection_id, uid, old_data, new_data"
				+ " FROM pending_change ORDER BY resource_id", null);
		try {
			while ( c.moveToNext() ) {
				rows.add(c.getInt(0) + "|" + c.getInt(1) + "|" + c.getString(2) + "|" + c.getString(3) + "|" + c.getString(4));
			}
		}
		finally {
			c.close();
		}
		return rows;
	}

	@Test
	public void aResourceWithNoNameIsQueuedAsNew() {
		String data = event("4beadad6-0c7a-4341-8c5c-923bf16869a0", "\r\n");
		resource(10, CALDAV_COLLECTION, null, data);

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		List<String> queued = pending();
		assertEquals(1, queued.size());
		assertEquals("10|1|4beadad6-0c7a-4341-8c5c-923bf16869a0|null|" + data, queued.get(0));
	}

	@Test
	public void theUidIsFoundWhateverTheLineEndings() {
		resource(10, CALDAV_COLLECTION, null, event("unix-lines@example.net", "\n"));
		resource(11, CALDAV_COLLECTION, null, "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:last-line");

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		List<String> queued = pending();
		assertTrue(queued.get(0), queued.get(0).startsWith("10|1|unix-lines@example.net|null|"));
		assertTrue(queued.get(1), queued.get(1).startsWith("11|1|last-line|null|"));
	}

	/** It is still sent, under a name made for it. */
	@Test
	public void aResourceWithNoUidIsQueuedWithNone() {
		resource(10, CALDAV_COLLECTION, null, "BEGIN:VCARD\r\nFN:Somebody\r\nEND:VCARD\r\n");

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		assertEquals(1, pending().size());
		Cursor c = db.rawQuery("SELECT uid FROM pending_change", null);
		c.moveToFirst();
		assertNull(c.getString(0));
		c.close();
	}

	@Test
	public void whatTheServerHasIsLeftAlone() {
		resource(10, CALDAV_COLLECTION, "abc.ics", event("abc", "\r\n"));

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		assertEquals(0, pending().size());
	}

	@Test
	public void aChangeAlreadyWaitingIsLeftAsItIs() {
		resource(10, CALDAV_COLLECTION, null, event("abc", "\r\n"));
		db.execSQL("INSERT INTO pending_change (collection_id, resource_id, old_data, new_data, uid)"
				+ " VALUES (1, 10, 'before', 'after', 'abc')");

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		List<String> queued = pending();
		assertEquals(1, queued.size());
		assertEquals("10|1|abc|before|after", queued.get(0));
	}

	@Test
	public void onlyCalDavAccountsAreLookedAt() {
		resource(10, OTHER_KIND_OF_COLLECTION, null, event("abc", "\r\n"));

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		assertEquals(0, pending().size());
	}

	@Test
	public void aRowWithNoDataIsLeftAlone() {
		resource(10, CALDAV_COLLECTION, null, null);

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		assertEquals(0, pending().size());
	}

	@Test
	public void runningItAgainQueuesNothingMore() {
		resource(10, CALDAV_COLLECTION, null, event("abc", "\r\n"));

		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);
		db.execSQL(AcalDBHelper.QUEUE_UNSENT_RESOURCES_SQL);

		assertEquals(1, pending().size());
	}
}
