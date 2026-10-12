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
 * What a resource is called and what kind of thing it holds, worked out from
 * its UID and its data.
 */
final class ResourceNaming {

	private ResourceNaming() {
	}

	/**
	 * @return true if a resource already has a name of its own.  The name read
	 *         back from a row with none is the word "null".
	 */
	static boolean hasName(String resourceName) {
		return resourceName != null && !resourceName.equals("null");
	}

	/**
	 * @return a name for a resource which has none: its UID and an extension
	 *         for what it holds, or something random if the UID will not do.
	 */
	static String newName(String uid, String data) {
		return (isSafeName(uid) ? uid : UUID.randomUUID().toString()) + extensionFor(contentTypeOf(data));
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
