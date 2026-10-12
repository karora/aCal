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

import org.davical.acal.storage.CommitResult.Outcome;
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

	/**
	 * A resource whose first upload was given up on stays in the database with
	 * no name.  Changing it was sent as an update of "null" in the collection.
	 */
	@Test
	public void aChangeToAResourceWithNoNameIsSentAsNew() {
		for ( String noName : new String[] { null, "null" } ) {
			CalDavChange change = CalDavChange.plan(EVENT, EVENT.replace("abc", "abd"), noName, "abc", null);

			assertEquals("PUT", change.method());
			assertFalse(change.localOnly);
			assertEquals("abc.ics", change.resourceName);
			assertEquals("If-None-Match", change.matchHeader);
			assertEquals("*", change.matchValue);
		}
	}

	/** There is nothing on the server to delete, and "null" in the collection is not it. */
	@Test
	public void deletingAResourceWithNoNameSendsNothing() {
		for ( String noName : new String[] { null, "null" } ) {
			assertTrue(CalDavChange.plan(EVENT, null, noName, "abc", null).localOnly);
			assertTrue(CalDavChange.plan(EVENT, null, noName, "abc", null).isDelete);
			// Made and then deleted before it was ever sent.
			assertTrue(CalDavChange.plan(null, null, noName, "abc", null).localOnly);
		}
	}

	@Test
	public void everyChangeToAResourceWithANameIsSent() {
		assertFalse(CalDavChange.plan(null, EVENT, "abc.ics", "abc", null).localOnly);
		assertFalse(CalDavChange.plan(EVENT, EVENT, "abc.ics", "abc", "\"etag-1\"").localOnly);
		assertFalse(CalDavChange.plan(EVENT, null, "abc.ics", "abc", "\"etag-1\"").localOnly);
		assertFalse(CalDavChange.plan(EVENT, null, "abc.ics", "abc", null).localOnly);
	}

	@Test
	public void anOrdinaryUidIsUsedAsTheName() {
		for ( String uid : new String[] { "abc", "20261012T101500Z-1234@example.net",
					"5f1c2a9e-0b7d-4c57-9a50-3a1f0c2b7d11", "a.b_c-d" } ) {
			assertEquals(uid + ".ics", CalDavChange.plan(null, EVENT, null, uid, null).resourceName);
		}
	}

	/** The name goes on the end of the collection's path, so these would go somewhere else. */
	@Test
	public void aUidThatWouldChangeThePathIsNotUsedAsTheName() {
		for ( String uid : new String[] { "../other/calendar/abc", "a/b", "..", ".hidden", "abc?x=1", "abc#frag",
					"abc%2F..%2Fx", "a b", "a\\b", "abc\r\nX-Header: 1", "" } ) {
			String name = CalDavChange.plan(null, EVENT, null, uid, null).resourceName;
			assertTrue(uid + " gave " + name, name.matches("[0-9a-f-]{36}\\.ics"));
		}
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

	private static final CalDavChange CREATE = CalDavChange.plan(null, EVENT, null, "abc", null);
	private static final CalDavChange UPDATE = CalDavChange.plan(EVENT, EVENT, "abc.ics", "abc", "\"etag-1\"");
	private static final CalDavChange DELETE = CalDavChange.plan(EVENT, null, "abc.ics", "abc", "\"etag-1\"");
	private static final CalDavChange[] ANY = { CREATE, UPDATE, DELETE };

	@Test
	public void successIsApplied() {
		for ( CalDavChange change : ANY ) {
			assertEquals(Outcome.APPLIED, change.outcomeOf(200));
			assertEquals(Outcome.APPLIED, change.outcomeOf(201));
			assertEquals(Outcome.APPLIED, change.outcomeOf(204));
		}
	}

	@Test
	public void aRefusalIsRefused() {
		for ( CalDavChange change : ANY ) {
			for ( int status : new int[] { 400, 403, 405, 409, 412, 413, 415 } ) {
				assertEquals("status " + status, Outcome.REFUSED, change.outcomeOf(status));
			}
		}
	}

	/** These were all given up on, and the change was lost with nothing said. */
	@Test
	public void whatMayPassLaterIsTriedAgain() {
		for ( CalDavChange change : ANY ) {
			for ( int status : new int[] { 401, 407, 408, 423, 425, 429 } ) {
				assertEquals("status " + status, Outcome.RETRY, change.outcomeOf(status));
			}
		}
	}

	@Test
	public void aServerErrorIsTriedAgain() {
		for ( CalDavChange change : ANY ) {
			for ( int status : new int[] { 500, 502, 503, 507 } ) {
				assertEquals("status " + status, Outcome.RETRY, change.outcomeOf(status));
			}
		}
	}

	/** A redirect that was not followed, or an answer that is not one, says nothing about the change. */
	@Test
	public void anAnswerThatSettlesNothingIsTriedAgain() {
		for ( CalDavChange change : ANY ) {
			for ( int status : new int[] { -1, 0, 100, 202, 207, 301, 302, 307, 308 } ) {
				assertEquals("status " + status, Outcome.RETRY, change.outcomeOf(status));
			}
		}
	}

	@Test
	public void deletingWhatIsNotThereIsDone() {
		assertEquals(Outcome.APPLIED, DELETE.outcomeOf(404));
		assertEquals(Outcome.APPLIED, DELETE.outcomeOf(410));
	}

	@Test
	public void changingWhatIsNotThereIsRefused() {
		assertEquals(Outcome.REFUSED, UPDATE.outcomeOf(404));
		assertEquals(Outcome.REFUSED, UPDATE.outcomeOf(410));
		assertEquals(Outcome.REFUSED, CREATE.outcomeOf(404));
	}
}
