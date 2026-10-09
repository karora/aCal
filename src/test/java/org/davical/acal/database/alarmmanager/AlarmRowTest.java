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


package org.davical.acal.database.alarmmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.davical.acal.providers.AlarmDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;

@RunWith(RobolectricTestRunner.class)
public class AlarmRowTest {

	private static final long MINUTE = 60 * 1000L;
	private static final long DAY = 24 * 60 * MINUTE;
	private static final long SNOOZE = 9 * MINUTE;
	private static final String RRID = "RECURRENCE-ID;TZID=Pacific/Auckland:20260119T090000";

	@Test
	public void survivesBeingStoredAndReadBack() {
		AlarmRow row = new AlarmRow(42, 1000 * DAY, 1000 * DAY + SNOOZE, 7, RRID, ALARM_STATE.SNOOZED, "BEGIN:VALARM");
		ContentValues stored = row.toContentValues();
		AlarmRow read = AlarmRow.fromContentValues(stored);

		assertEquals(stored, read.toContentValues());
		assertEquals(42, read.getId());
		assertEquals(1000 * DAY + SNOOZE, read.getTimeToFire());
		assertEquals("BEGIN:VALARM", read.getBlob());
	}

	/**
	 * The numbers of the states are what is stored, so the states must stay
	 * in this order, with any new one added at the end.
	 */
	@Test
	public void statesKeepTheNumbersThatAreInTheDatabase() {
		List<String> names = new ArrayList<String>();
		for( ALARM_STATE state : ALARM_STATE.values() ) names.add(state.name());
		assertEquals(Arrays.asList("PENDING", "SNOOZED", "DISMISSED", "FIRED"), names);
	}

	@Test
	public void aRowThatWasNeverStoredHasNoId() {
		AlarmRow row = new AlarmRow(1000 * DAY, 7, RRID, "BEGIN:VALARM");
		ContentValues stored = row.toContentValues();
		assertTrue(!stored.containsKey(AlarmDataProvider._ID));
		assertEquals(Integer.valueOf(ALARM_STATE.PENDING.ordinal()), stored.getAsInteger(AlarmDataProvider.STATE));
		assertEquals(stored.getAsLong(AlarmDataProvider.TIME_TO_FIRE), stored.getAsLong(AlarmDataProvider.BASE_TIME_TO_FIRE));
	}

	/**
	 * A snooze counts from when the button is pressed: the alarm may have
	 * gone off a long time before that.
	 */
	@Test
	public void snoozingAnAlarmThatHasGoneOffCountsFromNow() {
		long before = System.currentTimeMillis();
		AlarmRow row = new AlarmRow(before - 3 * DAY, 7, RRID, "BEGIN:VALARM");
		row.addSnooze();
		long after = System.currentTimeMillis();

		assertTrue(row.getTimeToFire() >= before + SNOOZE);
		assertTrue(row.getTimeToFire() <= after + SNOOZE);
	}

	@Test
	public void snoozingAnAlarmThatIsStillToComeCountsFromWhenItIsDue() {
		long due = System.currentTimeMillis() + DAY;
		AlarmRow row = new AlarmRow(due, 7, RRID, "BEGIN:VALARM");
		row.addSnooze();
		assertEquals(due + SNOOZE, row.getTimeToFire());
	}

	@Test
	public void snoozingDoesNotChangeWhenTheAlarmWasFirstDue() {
		long due = System.currentTimeMillis() + DAY;
		AlarmRow row = new AlarmRow(due, 7, RRID, "BEGIN:VALARM");
		row.addSnooze();
		assertEquals(Long.valueOf(due), row.toContentValues().getAsLong(AlarmDataProvider.BASE_TIME_TO_FIRE));
	}

	@Test
	public void sortsInTheOrderTheAlarmsAreDue() {
		long now = 1800000000000L;
		// Further apart than fits in an int of milliseconds, which is about 25 days
		AlarmRow soon = new AlarmRow(now + MINUTE, 1, RRID, "soon");
		AlarmRow nextMonth = new AlarmRow(now + 40 * DAY, 2, RRID, "next month");
		AlarmRow nextYear = new AlarmRow(now + 400 * DAY, 3, RRID, "next year");

		List<AlarmRow> rows = new ArrayList<AlarmRow>(Arrays.asList(nextYear, soon, nextMonth));
		Collections.sort(rows);

		List<String> order = new ArrayList<String>();
		for( AlarmRow row : rows ) order.add(row.getBlob());
		assertEquals(Arrays.asList("soon", "next month", "next year"), order);
	}
}
