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

import android.content.ContentValues;

import org.davical.acal.providers.Servers;

/**
 * Where the collections of an account are kept.  Each row of dav_server is an
 * account, and its backend_type column holds one of these.
 */
public enum BackendType {
	/** On a CalDAV or CardDAV server */
	CALDAV(0),
	/** In our own database on the phone, and nowhere else */
	LOCAL(1);

	/** The number stored in the database, which must never change. */
	public final int value;

	private BackendType(int value) {
		this.value = value;
	}

	/**
	 * @param stored the value of backend_type, or null if there is none
	 * @return the type, or null if this version of aCal does not know it.
	 *         Rows written before the column existed are CalDAV.
	 */
	public static BackendType fromValue(Integer stored) {
		if ( stored == null ) return CALDAV;
		for ( BackendType type : values() ) {
			if ( type.value == stored ) return type;
		}
		return null;
	}

	public static BackendType fromServerRow(ContentValues serverData) {
		return fromValue(serverData.getAsInteger(Servers.BACKEND_TYPE));
	}
}
