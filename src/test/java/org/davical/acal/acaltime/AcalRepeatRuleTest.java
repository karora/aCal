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

package org.davical.acal.acaltime;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;
import org.junit.Test;

public class AcalRepeatRuleTest {

	private static final String ZONE = "Pacific/Auckland";

	private static String event(String... recurrenceLines) {
		StringBuilder blob = new StringBuilder(
				"BEGIN:VCALENDAR\r\n" +
				"VERSION:2.0\r\n" +
				"PRODID:-//aCal//test//EN\r\n" +
				"BEGIN:VEVENT\r\n" +
				"UID:repeat-1\r\n" +
				"DTSTAMP:20260101T000000Z\r\n" +
				"DTSTART;TZID=" + ZONE + ":20260105T090000\r\n" +
				"DTEND;TZID=" + ZONE + ":20260105T100000\r\n" +
				"SUMMARY:Repeating\r\n");
		for( String line : recurrenceLines ) blob.append(line).append("\r\n");
		return blob.append("END:VEVENT\r\nEND:VCALENDAR\r\n").toString();
	}

	/** The instances in the first three months of 2026, in iCalendar format. */
	private static List<String> instances(String blob) {
		VCalendar calendar = (VCalendar) VComponent.createComponentFromBlob(blob);
		AcalRepeatRule rule = AcalRepeatRule.fromVCalendar(calendar, 1, 1);
		List<String> found = new ArrayList<String>();
		for( AcalDateTime instance : rule.getInstancesInRange(
				AcalDateTime.fromIcalendar("20260101T000000", null, ZONE),
				AcalDateTime.fromIcalendar("20260401T000000", null, ZONE)) ) {
			found.add(instance.fmtIcal());
		}
		return found;
	}

	@Test
	public void dailyWithCount() {
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000"),
				instances(event("RRULE:FREQ=DAILY;COUNT=3")));
	}

	@Test
	public void weeklyOnSeveralDaysUntilADate() {
		// 5 January 2026 is a Monday
		assertEquals(Arrays.asList("20260105T090000", "20260107T090000", "20260109T090000",
						"20260112T090000", "20260114T090000"),
				instances(event("RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20260114T235959Z")));
	}

	/**
	 * Each EXDATE line has to be obeyed, not only the last, and an excluded
	 * instance still counts towards COUNT.
	 */
	@Test
	public void excludedInstancesAreLeftOutButStillCounted() {
		assertEquals(Arrays.asList("20260105T090000", "20260107T090000", "20260109T090000"),
				instances(event("RRULE:FREQ=DAILY;COUNT=5",
						"EXDATE;TZID=" + ZONE + ":20260106T090000",
						"EXDATE;TZID=" + ZONE + ":20260108T090000")));
	}

	@Test
	public void severalExcludedDatesOnOneLine() {
		assertEquals(Arrays.asList("20260105T090000", "20260107T090000", "20260109T090000"),
				instances(event("RRULE:FREQ=DAILY;COUNT=5",
						"EXDATE;TZID=" + ZONE + ":20260106T090000,20260108T090000")));
	}
}
