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

package org.davical.acal.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.davical.acal.R;
import org.davical.acal.activity.UnsavedChange.Kind;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;

@RunWith(RobolectricTestRunner.class)
public class UnsavedChangeTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:abc\r\n"
			+ "DTSTART:20261014T041500Z\r\nDTEND:20261014T051500Z\r\nSUMMARY:Demo night\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	private static final String CARD = "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:abc\r\nFN:Ada Lovelace\r\nEND:VCARD\r\n";

	private static ContentValues pending(String name, String oldData, String newData, Integer status) {
		ContentValues pending = new ContentValues();
		pending.put(ResourceTableManager.PENDING_ID, 51L);
		pending.put(ResourceTableManager.PEND_COLLECTION_ID, 37L);
		pending.put(ResourceTableManager.RESOURCE_NAME, name);
		pending.put(ResourceTableManager.OLD_DATA, oldData);
		pending.put(ResourceTableManager.NEW_DATA, newData);
		pending.put(ResourceTableManager.PEND_STATE, ResourceTableManager.PENDING_REFUSED);
		pending.put(ResourceTableManager.PEND_LAST_STATUS, status);
		return pending;
	}

	@Test
	public void theKindIsWhatTheServerWouldSeeHappen() {
		assertEquals(Kind.NEW, UnsavedChange.kindOf(null, null, EVENT));
		assertEquals(Kind.CHANGED, UnsavedChange.kindOf("abc.ics", EVENT, EVENT));
		assertEquals(Kind.DELETED, UnsavedChange.kindOf("abc.ics", EVENT, null));
	}

	/** It has been changed here before, but the server has never had it. */
	@Test
	public void aChangeToWhatTheServerNeverHadIsNew() {
		assertEquals(Kind.NEW, UnsavedChange.kindOf(null, EVENT, EVENT));
		assertEquals(Kind.NEW, UnsavedChange.kindOf("null", EVENT, EVENT));
	}

	@Test
	public void aChangeIsReadFromItsRow() {
		UnsavedChange change = new UnsavedChange(pending("abc.ics", EVENT, EVENT.replace("Demo night", "Demo evening"), 403));

		assertEquals(51L, change.pendingId);
		assertEquals(37L, change.collectionId);
		assertEquals(Kind.CHANGED, change.kind);
		assertEquals(403, change.status);
		assertEquals("Demo evening", change.title);
		assertEquals("20261014T041500Z", change.start.fmtIcal());
		assertEquals(R.string.unsaved_reason_forbidden, change.reason());
	}

	@Test
	public void whatIsBeingDeletedIsDescribedByWhatItWas() {
		UnsavedChange change = new UnsavedChange(pending("abc.ics", EVENT, null, 412));

		assertEquals(Kind.DELETED, change.kind);
		assertEquals("Demo night", change.title);
	}

	@Test
	public void aContactIsCalledByItsName() {
		UnsavedChange change = new UnsavedChange(pending(null, null, CARD, 403));

		assertEquals("Ada Lovelace", change.title);
		assertNull(change.start);
	}

	@Test
	public void whatCannotBeReadIsStillAChange() {
		UnsavedChange change = new UnsavedChange(pending(null, null, "not a calendar at all", null));

		assertEquals(Kind.NEW, change.kind);
		assertNull(change.title);
		assertNull(change.start);
		assertEquals(0, change.status);
		assertEquals(R.string.unsaved_reason_other, change.reason());
	}

	@Test
	public void theReasonGoesWithTheAnswer() {
		for ( Kind kind : Kind.values() ) {
			assertEquals(R.string.unsaved_reason_forbidden, UnsavedChange.reasonFor(kind, 403));
			assertEquals(R.string.unsaved_reason_gone, UnsavedChange.reasonFor(kind, 404));
			assertEquals(R.string.unsaved_reason_gone, UnsavedChange.reasonFor(kind, 410));
			assertEquals(R.string.unsaved_reason_too_large, UnsavedChange.reasonFor(kind, 413));
			assertEquals(R.string.unsaved_reason_other, UnsavedChange.reasonFor(kind, 400));
			assertEquals(R.string.unsaved_reason_other, UnsavedChange.reasonFor(kind, 415));
		}
	}

	/** For something new, "it has changed on the server" would make no sense. */
	@Test
	public void aConflictOverSomethingNewIsThatTheNameIsTaken() {
		for ( int status : new int[] { 409, 412 } ) {
			assertEquals(R.string.unsaved_reason_exists, UnsavedChange.reasonFor(Kind.NEW, status));
			assertEquals(R.string.unsaved_reason_conflict, UnsavedChange.reasonFor(Kind.CHANGED, status));
			assertEquals(R.string.unsaved_reason_conflict, UnsavedChange.reasonFor(Kind.DELETED, status));
		}
	}
}
