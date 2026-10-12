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

import org.davical.acal.storage.CommitResult.Outcome;

/**
 * What to send to a CalDAV server to commit one pending change, and what the
 * answer means.  Nothing in here touches the database or the network.
 */
final class CalDavChange {

	final boolean isDelete;
	/** True if the server has never had the resource, so there is nothing to send to it. */
	final boolean localOnly;
	/** The name of the resource within its collection. */
	final String resourceName;
	/** If-Match or If-None-Match, or null to send neither. */
	final String matchHeader;
	final String matchValue;
	final String contentType;

	private CalDavChange(boolean isDelete, boolean localOnly, String resourceName, String matchHeader,
				String matchValue, String contentType) {
		this.isDelete = isDelete;
		this.localOnly = localOnly;
		this.resourceName = resourceName;
		this.matchHeader = matchHeader;
		this.matchValue = matchValue;
		this.contentType = contentType;
	}

	String method() {
		return (isDelete ? "DELETE" : "PUT");
	}

	/**
	 * A resource gets its name when the server accepts it, so one with no name
	 * is not on the server whatever went before: a change to it is sent as new,
	 * and deleting it needs nothing sent at all.
	 *
	 * @param oldData what the resource was before the change, null if it is new
	 * @param newData what it is to become, null if it is being deleted
	 * @param resourceName the name it has on the server, if it has one yet
	 * @param uid the UID, which a new resource is named after
	 * @param etag the etag the server last gave for it
	 */
	static CalDavChange plan(String oldData, String newData, String resourceName, String uid, String etag) {
		String contentType = ResourceNaming.contentTypeOf(newData);

		boolean unnamed = !ResourceNaming.hasName(resourceName);

		if ( unnamed && newData == null ) {
			return new CalDavChange(true, true, null, null, null, contentType);
		}
		if ( unnamed ) {
			resourceName = ResourceNaming.newName(uid, newData);
		}
		if ( unnamed || oldData == null ) {
			return new CalDavChange(false, false, resourceName, "If-None-Match", "*", contentType);
		}

		return new CalDavChange(newData == null, false, resourceName, (etag == null ? null : "If-Match"), etag,
					contentType);
	}

	/**
	 * @param status what the server answered when this change was sent
	 */
	Outcome outcomeOf(int status) {
		switch ( status ) {
			case 200: // OK, normal for an update
			case 201: // Created, normal for a new resource
			case 204: // No Content, normal for a delete
				return Outcome.APPLIED;

			case 404: // Not Found
			case 410: // Gone
				// What we wanted rid of is not there, which is all we asked
				return (isDelete ? Outcome.APPLIED : Outcome.REFUSED);

			case 401: // Unauthorized: the password may be put right
			case 407: // Proxy Authentication Required
			case 408: // Request Timeout
			case 423: // Locked, by someone who will let go of it
			case 425: // Too Early
			case 429: // Too Many Requests
				return Outcome.RETRY;
		}
		// Anything else the server blames on the request will be no different
		// next time.  The rest (a redirect we did not follow, a server error,
		// something that makes no sense as an answer) may be.
		return (status >= 400 && status < 500 ? Outcome.REFUSED : Outcome.RETRY);
	}
}
