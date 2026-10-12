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

import java.util.ArrayList;
import java.util.UUID;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.davical.acal.Constants;
import org.davical.acal.StaticHelpers;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.requests.RRDeleteByCollectionId;
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

	/**
	 * @return true if this dav_server row is the account for what is kept on the phone
	 */
	public static boolean isLocalAccount(ContentValues serverData) {
		return serverData != null && BackendType.fromServerRow(serverData) == BackendType.LOCAL;
	}

	/**
	 * @return true if this dav_collection row is a collection kept on the phone
	 */
	public static boolean isLocal(ContentValues collectionData, ContentResolver cr) {
		Integer serverId = (collectionData == null ? null : collectionData.getAsInteger(DavCollections.SERVER_ID));
		return serverId != null && isLocalAccount(Servers.getRow(serverId, cr));
	}

	/**
	 * @return what a new collection on the phone starts out as, for the user
	 *         to change before it is made.  It can hold anything, and what it
	 *         is used for is a matter of what it is active for.
	 */
	public static ContentValues newCalendar(ContentResolver cr, String displayName) {
		return valuesFor(accountId(cr), displayName, null, true, true, true);
	}

	/**
	 * Make sure there is at least one collection on the phone, for someone who
	 * is using the app with no server.
	 */
	public static void ensureOne(ContentResolver cr, String displayName) {
		int accountId = accountId(cr);
		Cursor c = cr.query(DavCollections.CONTENT_URI, new String[] { DavCollections._ID },
					DavCollections.SERVER_ID+"="+accountId, null, null);
		try {
			if ( c.getCount() > 0 ) return;
		}
		finally {
			c.close();
		}
		cr.insert(DavCollections.CONTENT_URI, valuesFor(accountId, displayName, null, true, true, true));
		Collection.flush();
	}

	/**
	 * Delete a collection kept on the phone and everything in it.  There is no
	 * other copy, so this cannot be undone.
	 */
	public static void delete(Context context, long collectionId) {
		ContentResolver cr = context.getContentResolver();
		cr.delete(Uri.withAppendedPath(DavCollections.CONTENT_URI, Long.toString(collectionId)), null, null);

		ArrayList<Long> collectionIds = new ArrayList<Long>();
		collectionIds.add(collectionId);
		ResourceManager.getInstance(context).sendRequest(new RRDeleteByCollectionId(collectionIds));
		Collection.flush();
	}

	static ContentValues valuesFor(int accountId, String displayName, String colour,
				boolean holdsEvents, boolean holdsTasks, boolean holdsJournal) {
		// Numbers and not booleans, which is how the row reads back from the
		// database and what the screens that edit it expect.
		ContentValues collection = new ContentValues();
		collection.put(DavCollections.SERVER_ID, accountId);
		// There is no path to it, but each collection of an account must have its own
		collection.put(DavCollections.COLLECTION_PATH, "local:" + UUID.randomUUID().toString() + "/");
		collection.put(DavCollections.DISPLAYNAME, displayName);
		collection.put(DavCollections.COLOUR, (colour == null ? StaticHelpers.randomColorString() : colour));

		collection.put(DavCollections.HOLDS_EVENTS, holdsEvents ? 1 : 0);
		collection.put(DavCollections.HOLDS_TASKS, holdsTasks ? 1 : 0);
		collection.put(DavCollections.HOLDS_JOURNAL, holdsJournal ? 1 : 0);
		collection.put(DavCollections.HOLDS_ADDRESSBOOK, 0);
		collection.put(DavCollections.ACTIVE_EVENTS, holdsEvents ? 1 : 0);
		collection.put(DavCollections.ACTIVE_TASKS, holdsTasks ? 1 : 0);
		collection.put(DavCollections.ACTIVE_JOURNAL, holdsJournal ? 1 : 0);
		collection.put(DavCollections.ACTIVE_ADDRESSBOOK, 0);

		collection.put(DavCollections.IS_WRITABLE, 1);
		collection.put(DavCollections.IS_VISIBLE, 1);
		collection.put(DavCollections.USE_ALARMS, 1);
		collection.put(DavCollections.MAX_SYNC_AGE_WIFI, Constants.DEFAULT_MAX_AGE_WIFI);
		collection.put(DavCollections.MAX_SYNC_AGE_3G, Constants.DEFAULT_MAX_AGE_3G);

		// It is all here already, and always will be
		collection.put(DavCollections.NEEDS_SYNC, 0);
		collection.put(DavCollections.SYNC_METADATA, 0);
		collection.put(DavCollections.LAST_SYNCHRONISED, AcalDateTime.getUTCInstance().fmtIcal());
		return collection;
	}
}
