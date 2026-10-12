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

package org.davical.acal.database.cachemanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;

@RunWith(RobolectricTestRunner.class)
public class CacheChangedEventTest {

	private static final long DAY = 24 * 60 * 60 * 1000L;
	private static final long RANGE_START = 1000 * DAY;
	private static final long RANGE_END = 1040 * DAY;
	/** What the cache holds for a time that is not set */
	private static final long NOT_SET = Long.MAX_VALUE;

	private static ContentValues row(Long start, Long end, Long completed) {
		ContentValues row = new ContentValues();
		row.put(CacheTableManager.FIELD_DTSTART, start);
		row.put(CacheTableManager.FIELD_DTEND, end);
		row.put(CacheTableManager.FIELD_COMPLETED, completed);
		return row;
	}

	private static boolean affects(ContentValues row, boolean listCompleted) {
		return CacheChangedEvent.mayAffectList(row, listCompleted, RANGE_START, RANGE_END);
	}

	/**
	 * A new task with no start and no due date did not appear in the list of
	 * tasks until something else refreshed it, and nor did a note.
	 */
	@Test
	public void aTaskOrNoteWithNoDatesIsAlwaysInTheList() {
		assertTrue(affects(row(NOT_SET, NOT_SET, NOT_SET), false));
		assertTrue(affects(row(NOT_SET, NOT_SET, NOT_SET), true));
	}

	@Test
	public void oneWithOnlyAStartOrOnlyAnEndIsInTheListToo() {
		assertTrue(affects(row(1010 * DAY, NOT_SET, NOT_SET), false));
		assertTrue(affects(row(NOT_SET, 1010 * DAY, NOT_SET), false));
		// Even long before or after: without the other end we cannot say it is outside
		assertTrue(affects(row(2000 * DAY, NOT_SET, NOT_SET), false));
		assertTrue(affects(row(NOT_SET, 10 * DAY, NOT_SET), false));
	}

	@Test
	public void oneWithBothDatesIsInTheListIfItOverlapsTheRange() {
		assertTrue(affects(row(1010 * DAY, 1011 * DAY, NOT_SET), false));
		assertTrue(affects(row(990 * DAY, 1001 * DAY, NOT_SET), false));
		assertTrue(affects(row(1039 * DAY, 1050 * DAY, NOT_SET), false));

		assertFalse(affects(row(900 * DAY, 901 * DAY, NOT_SET), false));
		assertFalse(affects(row(1100 * DAY, 1101 * DAY, NOT_SET), false));
	}

	@Test
	public void aCompletedOneOnlyMattersToAListOfCompletedOnes() {
		assertFalse(affects(row(NOT_SET, NOT_SET, 1010 * DAY), false));
		assertTrue(affects(row(NOT_SET, NOT_SET, 1010 * DAY), true));
		assertFalse(affects(row(1010 * DAY, 1011 * DAY, 1010 * DAY), false));
		assertTrue(affects(row(1010 * DAY, 1011 * DAY, 1010 * DAY), true));
	}

	/** A row with a column missing made the list throw an exception, not refresh. */
	@Test
	public void aRowWithColumnsMissingIsTakenToMatter() {
		assertTrue(affects(new ContentValues(), false));
		assertTrue(affects(row(null, null, null), false));
	}
}
