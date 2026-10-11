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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.davical.acal.storage.CalDavChange.Outcome;
import org.junit.Test;

public class CalDavChangeTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	private static final String CARD = "BEGIN:VCARD\r\nUID:abc\r\nEND:VCARD\r\n";

	@Test
	public void aNewResourceIsPutWhereThereIsNothingYet() {
		CalDavChange change = CalDavChange.plan(null, EVENT, null, "abc", null);

		assertEquals("PUT", change.method());
		assertFalse(change.isDelete);
		assertEquals("If-None-Match", change.matchHeader);
		assertEquals("*", change.matchValue);
		assertEquals("text/calendar; charset=\"utf-8\"", change.contentType);
	}

	@Test
	public void aNewResourceIsNamedAfterItsUid() {
		assertEquals("abc.ics", CalDavChange.plan(null, EVENT, null, "abc", null).resourceName);
		assertEquals("abc.vcf", CalDavChange.plan(null, CARD, null, "abc", null).resourceName);
		assertEquals("abc.txt", CalDavChange.plan(null, "something else entirely", null, "abc", null).resourceName);
	}

	/** The name read back from a row with no name is the word "null". */
	@Test
	public void aNameOfTheWordNullIsNoName() {
		assertEquals("abc.ics", CalDavChange.plan(null, EVENT, "null", "abc", null).resourceName);
	}

	@Test
	public void aNewResourceKeepsANameItAlreadyHas() {
		assertEquals("given.ics", CalDavChange.plan(null, EVENT, "given.ics", "abc", null).resourceName);
	}

	/** Two of these would otherwise both be "null.ics", and the second would be refused. */
	@Test
	public void aNewResourceWithNoUidGetsANameOfItsOwn() {
		String first = CalDavChange.plan(null, EVENT, null, null, null).resourceName;
		String second = CalDavChange.plan(null, EVENT, null, null, null).resourceName;

		assertTrue(first, first.endsWith(".ics"));
		assertFalse(first, first.startsWith("null"));
		assertFalse(first.equals(second));
	}

	@Test
	public void anUpdateIsPutOnlyIfTheServerStillHasWhatWeLastSaw() {
		CalDavChange change = CalDavChange.plan(EVENT, EVENT.replace("abc", "abd"), "abc.ics", "abc", "\"etag-1\"");

		assertEquals("PUT", change.method());
		assertEquals("abc.ics", change.resourceName);
		assertEquals("If-Match", change.matchHeader);
		assertEquals("\"etag-1\"", change.matchValue);
	}

	@Test
	public void anUpdateWithNoEtagIsSentUnconditionally() {
		CalDavChange change = CalDavChange.plan(EVENT, EVENT, "abc.ics", "abc", null);

		assertNull(change.matchHeader);
		assertNull(change.matchValue);
	}

	@Test
	public void aChangeToNothingIsADelete() {
		CalDavChange change = CalDavChange.plan(EVENT, null, "abc.ics", "abc", "\"etag-1\"");

		assertEquals("DELETE", change.method());
		assertTrue(change.isDelete);
		assertEquals("abc.ics", change.resourceName);
		assertEquals("If-Match", change.matchHeader);
		assertEquals("text/plain", change.contentType);
	}

	@Test
	public void theContentTypeComesFromHowTheDataBegins() {
		assertEquals("text/calendar; charset=\"utf-8\"", CalDavChange.contentTypeOf(EVENT));
		assertEquals("text/calendar; charset=\"utf-8\"", CalDavChange.contentTypeOf(EVENT.toLowerCase()));
		assertEquals("text/vcard; charset=\"utf-8\"", CalDavChange.contentTypeOf(CARD));
		assertEquals("text/plain", CalDavChange.contentTypeOf("<html>not what was asked for</html>"));
		assertEquals("text/plain", CalDavChange.contentTypeOf("short"));
		assertEquals("text/plain", CalDavChange.contentTypeOf(null));
	}

	@Test
	public void successIsApplied() {
		assertEquals(Outcome.APPLIED, CalDavChange.outcomeOf(200));
		assertEquals(Outcome.APPLIED, CalDavChange.outcomeOf(201));
		assertEquals(Outcome.APPLIED, CalDavChange.outcomeOf(204));
	}

	@Test
	public void aRefusalIsAbandoned() {
		for ( int status : new int[] { 403, 404, 405, 409, 412 } ) {
			assertEquals("status " + status, Outcome.ABANDONED, CalDavChange.outcomeOf(status));
		}
	}

	/** Characterisation: anything else below 500 is dropped as well, even a 401. */
	@Test
	public void anyOtherAnswerBelow500IsAbandoned() {
		for ( int status : new int[] { 207, 302, 400, 401, 423 } ) {
			assertEquals("status " + status, Outcome.ABANDONED, CalDavChange.outcomeOf(status));
		}
	}

	@Test
	public void aServerErrorIsTriedAgainLater() {
		for ( int status : new int[] { 500, 502, 503, 507 } ) {
			assertEquals("status " + status, Outcome.RETRY, CalDavChange.outcomeOf(status));
		}
	}
}
