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

import android.content.ContentResolver;
import android.content.ContentValues;

import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;

/**
 * Finds the backend for an account or a collection.  All of these return null
 * if the row cannot be read, or the account is of a type this version of aCal
 * does not know: the caller should then leave it alone.
 */
public final class StorageBackends {

	private StorageBackends() {
	}

	public static StorageBackend forType(BackendType type) {
		if ( type == null ) return null;
		switch ( type ) {
			case CALDAV:
				return new CalDavBackend();
			case LOCAL:
				return new LocalBackend();
		}
		return null;
	}

	public static StorageBackend forServerRow(ContentValues serverData) {
		if ( serverData == null ) return null;
		return forType(BackendType.fromServerRow(serverData));
	}

	public static StorageBackend forServer(int serverId, ContentResolver cr) {
		return forServerRow(Servers.getRow(serverId, cr));
	}

	public static StorageBackend forCollection(long collectionId, ContentResolver cr) {
		ContentValues collectionData = DavCollections.getRow(collectionId, cr);
		if ( collectionData == null ) return null;
		Integer serverId = collectionData.getAsInteger(DavCollections.SERVER_ID);
		if ( serverId == null ) return null;
		return forServer(serverId, cr);
	}
}
