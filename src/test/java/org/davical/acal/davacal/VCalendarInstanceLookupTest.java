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


package org.davical.acal.davacal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Which component an instance of a repeating event takes its details from:
 * the master, or one of the changed instances.  Each case is checked for a
 * calendar as it is when being displayed, and as it is when being edited,
 * when it keeps hold of its components.
 */
@RunWith(Parameterized.class)
public class VCalendarInstanceLookupTest {

	private static final String ZONE = "Pacific/Auckland";

	private static final String MASTER = event(
			"DTSTART;TZID=" + ZONE + ":20260105T090000\r\nRRULE:FREQ=WEEKLY;COUNT=6\r\n", "Weekly");
	/** The instance on the 12th moved to the afternoon. */
	private static final String ONE_CHANGED = event(
			"RECURRENCE-ID;TZID=" + ZONE + ":20260112T090000\r\nDTSTART;TZID=" + ZONE + ":20260112T140000\r\n", "Only the 12th");
	/** Every instance from the 19th an hour later. */
	private static final String LATER_ONES_CHANGED = event(
			"RECURRENCE-ID;TZID=" + ZONE + ";RANGE=THISANDFUTURE:20260119T090000\r\nDTSTART;TZID=" + ZONE + ":20260119T100000\r\n",
			"From the 19th");

	private static String event(String lines, String summary) {
		return "BEGIN:VEVENT\r\nUID:lookup-1\r\n" + lines + "DURATION:PT1H\r\nSUMMARY:" + summary + "\r\nEND:VEVENT\r\n";
	}

	@Parameters(name = "{0}")
	public static Collection<Object[]> states() {
		return Arrays.asList(new Object[][] { { "displaying", false }, { "editing", true } });
	}

	private final boolean editing;

	public VCalendarInstanceLookupTest(String name, boolean editing) {
		this.editing = editing;
	}

	private VCalendar calendar(String... events) {
		StringBuilder blob = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
		for( String event : events ) blob.append(event);
		VCalendar calendar = (VCalendar) VComponent.createComponentFromBlob(blob.append("END:VCALENDAR\r\n").toString());
		if ( editing ) calendar.setEditable();
		return calendar;
	}

	/** The summary and start of the instance that the rule puts at 9am on this day of January 2026. */
	private static String instance(VCalendar calendar, int day) {
		Masterable instance = calendar.getChildFromRecurrenceId(RecurrenceId.fromString(
				String.format("RECURRENCE-ID;TZID=%s:202601%02dT090000", ZONE, day)));
		return instance.getSummary() + " " + instance.getStart().fmtIcal();
	}

	@Test
	public void withNoChangedInstancesEveryInstanceComesFromTheMaster() {
		VCalendar calendar = calendar(MASTER);
		assertEquals("Weekly 20260105T090000", instance(calendar, 5));
		assertEquals("Weekly 20260126T090000", instance(calendar, 26));
		assertEquals("Weekly 20260112T090000", instance(calendar, 12));
	}

	/**
	 * Every instance of an event used to take the details of its one changed
	 * instance.
	 */
	@Test
	public void oneChangedInstanceAffectsOnlyItself() {
		VCalendar calendar = calendar(MASTER, ONE_CHANGED);
		assertEquals("Weekly 20260105T090000", instance(calendar, 5));
		assertEquals("Only the 12th 20260112T140000", instance(calendar, 12));
		assertEquals("Weekly 20260119T090000", instance(calendar, 19));
		assertEquals("Weekly 20260126T090000", instance(calendar, 26));
	}

	@Test
	public void aChangeToThisAndFutureAffectsLaterInstancesOnly() {
		VCalendar calendar = calendar(MASTER, LATER_ONES_CHANGED);
		assertEquals("Weekly 20260112T090000", instance(calendar, 12));
		assertEquals("From the 19th 20260119T100000", instance(calendar, 19));
		assertEquals("From the 19th 20260126T100000", instance(calendar, 26));
	}

	@Test
	public void bothKindsOfChangeTogether() {
		VCalendar calendar = calendar(MASTER, ONE_CHANGED, LATER_ONES_CHANGED);
		assertEquals("Weekly 20260105T090000", instance(calendar, 5));
		assertEquals("Only the 12th 20260112T140000", instance(calendar, 12));
		assertEquals("From the 19th 20260126T100000", instance(calendar, 26));
	}

	/**
	 * Asking about an instance used to turn the master into that instance,
	 * which is how editing one instance lost all of the others.
	 */
	@Test
	public void askingAboutAnInstanceDoesNotChangeTheCalendar() {
		VCalendar calendar = calendar(MASTER, LATER_ONES_CHANGED);
		instance(calendar, 12);
		instance(calendar, 26);

		Masterable master = calendar.getMasterChild();
		assertNull(master.getProperty(PropertyName.RECURRENCE_ID));
		assertEquals("20260105T090000", master.getStart().fmtIcal());
		assertEquals("From the 19th 20260119T100000", instance(calendar, 19));
	}
}
