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

import java.util.UUID;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.davical.acal.Constants;
import org.davical.acal.StaticHelpers;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.dataservice.Collection;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;

/**
 * The account for collections kept on the phone, and the making of them.
 */
public final class LocalCalendars {

	/** What the account is called in the database.  What the user is shown is translated. */
	public static final String ACCOUNT_NAME = "This phone";

	private LocalCalendars() {
	}

	/**
	 * @return the id of the dav_server row which is the account for local
	 *         collections.  There is one in every database, but if it has
	 *         gone missing it is put back.
	 */
	public static int accountId(ContentResolver cr) {
		Cursor c = cr.query(Servers.CONTENT_URI, new String[] { Servers._ID },
					Servers.BACKEND_TYPE+"="+BackendType.LOCAL.value, null, Servers._ID);
		try {
			if ( c.moveToFirst() ) return c.getInt(0);
		}
		finally {
			c.close();
		}

		ContentValues account = new ContentValues();
		account.put(Servers.FRIENDLY_NAME, ACCOUNT_NAME);
		account.put(Servers.ACTIVE, 1);
		account.put(Servers.BACKEND_TYPE, BackendType.LOCAL.value);
		Uri inserted = cr.insert(Servers.CONTENT_URI, account);
		return Integer.parseInt(inserted.getLastPathSegment());
	}

	/**
	 * Make a collection that is kept on the phone.
	 *
	 * @param colour as "#RRGGBB", or null for one picked at random
	 * @return the id of the new collection
	 */
	public static long create(ContentResolver cr, String displayName, String colour,
				boolean holdsEvents, boolean holdsTasks, boolean holdsJournal) {
		ContentValues collection = valuesFor(accountId(cr), displayName, colour, holdsEvents, holdsTasks, holdsJournal);
		Uri inserted = cr.insert(DavCollections.CONTENT_URI, collection);
		Collection.flush();
		return Long.parseLong(inserted.getLastPathSegment());
	}

	static ContentValues valuesFor(int accountId, String displayName, String colour,
				boolean holdsEvents, boolean holdsTasks, boolean holdsJournal) {
		ContentValues collection = new ContentValues();
		collection.put(DavCollections.SERVER_ID, accountId);
		// There is no path to it, but each collection of an account must have its own
		collection.put(DavCollections.COLLECTION_PATH, "local:" + UUID.randomUUID().toString() + "/");
		collection.put(DavCollections.DISPLAYNAME, displayName);
		collection.put(DavCollections.COLOUR, (colour == null ? StaticHelpers.randomColorString() : colour));

		collection.put(DavCollections.HOLDS_EVENTS, holdsEvents);
		collection.put(DavCollections.HOLDS_TASKS, holdsTasks);
		collection.put(DavCollections.HOLDS_JOURNAL, holdsJournal);
		collection.put(DavCollections.HOLDS_ADDRESSBOOK, false);
		collection.put(DavCollections.ACTIVE_EVENTS, holdsEvents);
		collection.put(DavCollections.ACTIVE_TASKS, holdsTasks);
		collection.put(DavCollections.ACTIVE_JOURNAL, holdsJournal);
		collection.put(DavCollections.ACTIVE_ADDRESSBOOK, false);

		collection.put(DavCollections.IS_WRITABLE, true);
		collection.put(DavCollections.IS_VISIBLE, true);
		collection.put(DavCollections.USE_ALARMS, true);
		collection.put(DavCollections.MAX_SYNC_AGE_WIFI, Constants.DEFAULT_MAX_AGE_WIFI);
		collection.put(DavCollections.MAX_SYNC_AGE_3G, Constants.DEFAULT_MAX_AGE_3G);

		// It is all here already, and always will be
		collection.put(DavCollections.NEEDS_SYNC, false);
		collection.put(DavCollections.SYNC_METADATA, false);
		collection.put(DavCollections.LAST_SYNCHRONISED, AcalDateTime.getUTCInstance().fmtIcal());
		return collection;
	}
}
