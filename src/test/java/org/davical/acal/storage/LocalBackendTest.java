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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.davical.acal.providers.DavCollections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;

@RunWith(RobolectricTestRunner.class)
public class LocalBackendTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

	private final LocalBackend backend = new LocalBackend();

	@Test
	public void committingNeedsNoNetwork() {
		assertFalse(backend.needsNetwork());
	}

	/** Or a job that talks to a server would be run for something that has none. */
	@Test
	public void thereIsNothingToFetchOrToFind() {
		assertNull(backend.discoverCollections(1));
		assertNull(backend.updateCollections(1));
		assertNull(backend.refreshCollection(1, false));
		assertNull(backend.refreshCollection(1, true));
		assertNull(backend.reloadCollection(1));
	}

	@Test
	public void aNewResourceIsNamedAfterItsUid() {
		assertEquals("abc.ics", LocalBackend.nameFor(null, "abc", EVENT));
		assertEquals("abc.ics", LocalBackend.nameFor("null", "abc", EVENT));
	}

	@Test
	public void aResourceKeepsTheNameItHas() {
		assertEquals("given.ics", LocalBackend.nameFor("given.ics", "abc", EVENT));
	}

	@Test
	public void aNewResourceWithNoUsableUidStillGetsAName() {
		for ( String uid : new String[] { null, "", "a/b" } ) {
			String name = LocalBackend.nameFor(null, uid, EVENT);
			assertTrue(uid + " gave " + name, name.matches("[0-9a-f-]{36}\\.ics"));
		}
	}

	@Test
	public void theEtagChangesWhenTheResourceDoes() {
		assertNotEquals(LocalBackend.newEtag(1791800000000L), LocalBackend.newEtag(1791800000001L));
		assertTrue(LocalBackend.newEtag(1791800000000L).matches("\"local-[0-9a-f]+\""));
	}

	@Test
	public void aNewLocalCalendarIsCompleteAndNeedsNothingFromAnywhere() {
		ContentValues collection = LocalCalendars.valuesFor(7, "On this phone", "#336699", true, true, false);

		assertEquals(Integer.valueOf(7), collection.getAsInteger(DavCollections.SERVER_ID));
		assertEquals("On this phone", collection.getAsString(DavCollections.DISPLAYNAME));
		assertEquals("#336699", collection.getAsString(DavCollections.COLOUR));
		assertTrue(collection.getAsBoolean(DavCollections.HOLDS_EVENTS));
		assertTrue(collection.getAsBoolean(DavCollections.ACTIVE_EVENTS));
		assertTrue(collection.getAsBoolean(DavCollections.ACTIVE_TASKS));
		assertFalse(collection.getAsBoolean(DavCollections.HOLDS_JOURNAL));
		assertFalse(collection.getAsBoolean(DavCollections.ACTIVE_JOURNAL));
		assertFalse(collection.getAsBoolean(DavCollections.ACTIVE_ADDRESSBOOK));
		assertTrue(collection.getAsBoolean(DavCollections.IS_WRITABLE));
		assertTrue(collection.getAsBoolean(DavCollections.IS_VISIBLE));
		assertFalse(collection.getAsBoolean(DavCollections.NEEDS_SYNC));
		assertFalse(collection.getAsBoolean(DavCollections.SYNC_METADATA));
	}

	/** dav_collection lets an account have each path only once. */
	@Test
	public void eachLocalCalendarHasAPathOfItsOwn() {
		String first = LocalCalendars.valuesFor(7, "One", null, true, false, false).getAsString(DavCollections.COLLECTION_PATH);
		String second = LocalCalendars.valuesFor(7, "Two", null, true, false, false).getAsString(DavCollections.COLLECTION_PATH);

		assertTrue(first, first.startsWith("local:"));
		assertNotEquals(first, second);
	}

	@Test
	public void aCalendarWithNoColourGivenGetsOne() {
		String colour = LocalCalendars.valuesFor(7, "One", null, true, false, false).getAsString(DavCollections.COLOUR);

		assertTrue(colour, colour.matches("#[0-9A-Fa-f]{6,8}"));
	}
}
