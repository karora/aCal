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

/**
 * What to send to a CalDAV server to commit one pending change, and what the
 * answer means.  Nothing in here touches the database or the network.
 */
final class CalDavChange {

	enum Outcome {
		/** The server has the change. */
		APPLIED,
		/** The server will never accept the change, so it is dropped. */
		ABANDONED,
		/** The server could not deal with it this time. */
		RETRY
	}

	final boolean isDelete;
	/** The name of the resource within its collection. */
	final String resourceName;
	/** If-Match or If-None-Match, or null to send neither. */
	final String matchHeader;
	final String matchValue;
	final String contentType;

	private CalDavChange(boolean isDelete, String resourceName, String matchHeader, String matchValue,
				String contentType) {
		this.isDelete = isDelete;
		this.resourceName = resourceName;
		this.matchHeader = matchHeader;
		this.matchValue = matchValue;
		this.contentType = contentType;
	}

	String method() {
		return (isDelete ? "DELETE" : "PUT");
	}

	/**
	 * @param oldData what the resource was before the change, null if it is new
	 * @param newData what it is to become, null if it is being deleted
	 * @param resourceName the name it has on the server, if it has one yet
	 * @param uid the UID, which a new resource is named after
	 * @param etag the etag the server last gave for it
	 */
	static CalDavChange plan(String oldData, String newData, String resourceName, String uid, String etag) {
		String contentType = contentTypeOf(newData);

		if ( oldData == null ) {
			if ( resourceName == null || resourceName.equals("null") ) {
				resourceName = (isSafeName(uid) ? uid : UUID.randomUUID().toString()) + extensionFor(contentType);
			}
			return new CalDavChange(false, resourceName, "If-None-Match", "*", contentType);
		}

		return new CalDavChange(newData == null, resourceName, (etag == null ? null : "If-Match"), etag, contentType);
	}

	/**
	 * The UID is whatever the writer of the calendar data chose, and the name
	 * goes on the end of the path of the collection.  One with a slash, a
	 * question mark or the like in it would send the request somewhere else,
	 * so only a UID of plain characters is used as a name.
	 */
	static boolean isSafeName(String uid) {
		return uid != null && uid.matches("[A-Za-z0-9][A-Za-z0-9._@-]*");
	}

	static Outcome outcomeOf(int status) {
		switch ( status ) {
			case 200: // OK, normal for an update
			case 201: // Created, normal for a new resource
			case 204: // No Content, normal for a delete
				return Outcome.APPLIED;
		}
		return (status < 500 ? Outcome.ABANDONED : Outcome.RETRY);
	}

	static String contentTypeOf(String data) {
		if ( data == null || data.length() < 15 ) return "text/plain";

		if ( data.substring(6, 15).equalsIgnoreCase("vcalendar") ) {
			return "text/calendar; charset=\"utf-8\"";
		}
		else if ( data.substring(6, 11).equalsIgnoreCase("vcard") ) {
			return "text/vcard; charset=\"utf-8\"";
		}
		return "text/plain";
	}

	private static String extensionFor(String contentType) {
		if ( contentType.startsWith("text/calendar") ) return ".ics";
		if ( contentType.startsWith("text/vcard") ) return ".vcf";
		return ".txt";
	}
}
