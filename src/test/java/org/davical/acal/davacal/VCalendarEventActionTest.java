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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.acaltime.AcalRepeatRule;
import org.davical.acal.activity.EventEdit;
import org.davical.acal.dataservice.EventInstance;
import org.junit.Ignore;
import org.junit.Test;

/**
 * What deleting or editing some of the instances of a repeating event does
 * to the calendar that is then sent to the server.
 */
public class VCalendarEventActionTest {

	private static final String ZONE = "Pacific/Auckland";
	private static final long COLLECTION_ID = 1;
	private static final long RESOURCE_ID = 1;

	private static final String MASTER =
			"BEGIN:VEVENT\r\n" +
			"UID:weekly-1\r\n" +
			"DTSTAMP:20260101T000000Z\r\n" +
			"DTSTART;TZID=" + ZONE + ":20260105T090000\r\n" +
			"DURATION:PT1H\r\n" +
			"SUMMARY:Weekly\r\n" +
			"RRULE:FREQ=WEEKLY;COUNT=6\r\n" +
			"%s" +
			"END:VEVENT\r\n";

	/** A weekly event on six Mondays from 5 January 2026. */
	private static String weekly(String extraMasterLines, String... overrides) {
		StringBuilder blob = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n");
		blob.append(String.format(MASTER, extraMasterLines));
		for( String override : overrides ) blob.append(override);
		return blob.append("END:VCALENDAR\r\n").toString();
	}

	/** The calendar as it is read from the database to have a change applied to it. */
	private static VCalendar stored(String blob) {
		return new VCalendar(new VComponent.ComponentParts(blob), COLLECTION_ID, RESOURCE_ID, null, null, null);
	}

	private static String override(String instance, String newStart, String summary) {
		return "BEGIN:VEVENT\r\n" +
				"UID:weekly-1\r\n" +
				"DTSTAMP:20260101T000000Z\r\n" +
				"RECURRENCE-ID;TZID=" + ZONE + ":" + instance + "\r\n" +
				"DTSTART;TZID=" + ZONE + ":" + newStart + "\r\n" +
				"DURATION:PT1H\r\n" +
				"SUMMARY:" + summary + "\r\n" +
				"END:VEVENT\r\n";
	}

	/**
	 * The instance of the event at this time, as the user would have picked
	 * it.  It comes from its own copy of the calendar, as it does in the app,
	 * because getChildFromRecurrenceId() changes the calendar it is called on.
	 */
	private static EventInstance instanceAt(String blob, String localTime) {
		RecurrenceId recurrenceId = RecurrenceId.fromString("RECURRENCE-ID;TZID=" + ZONE + ":" + localTime);
		return new EventInstance((VEvent) stored(blob).getChildFromRecurrenceId(recurrenceId),
				COLLECTION_ID, RESOURCE_ID, recurrenceId);
	}

	private static VCalendar parse(String blob) {
		return (VCalendar) VComponent.createComponentFromBlob(blob);
	}

	/** The days in 2026, as MMDD, on which the rule of the master event falls. */
	private static List<String> days(VCalendar calendar) {
		AcalRepeatRule rule = AcalRepeatRule.fromVCalendar(calendar, COLLECTION_ID, RESOURCE_ID);
		List<String> days = new ArrayList<String>();
		for( AcalDateTime instance : rule.getInstancesInRange(
				AcalDateTime.fromIcalendar("20260101T000000", null, ZONE),
				AcalDateTime.fromIcalendar("20270101T000000", null, ZONE)) ) {
			days.add(instance.fmtIcal().substring(4, 8));
		}
		return days;
	}

	private static List<String> summaries(VCalendar calendar) {
		List<String> summaries = new ArrayList<String>();
		for( VComponent child : calendar.getChildren() ) {
			if ( child instanceof VEvent ) summaries.add(((VEvent) child).getSummary());
		}
		return summaries;
	}

	@Test
	public void theEventAsWritten() {
		assertEquals(Arrays.asList("0105", "0112", "0119", "0126", "0202", "0209"), days(parse(weekly(""))));
	}

	@Test
	public void deletingOneInstanceExcludesIt() throws Exception {
		String calendar = weekly("");
		String blob = stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_SINGLE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("0105", "0112", "0126", "0202", "0209"), days(result));
		assertEquals("FREQ=WEEKLY;COUNT=6", result.getMasterChild().getRRule());
	}

	/**
	 * The instances already excluded used to be lost when another was added
	 * to them.
	 */
	@Test
	public void deletingOneInstanceKeepsEarlierExclusions() throws Exception {
		String calendar = weekly(
				"EXDATE;TZID=" + ZONE + ":20260112T090000\r\n" +
				"EXDATE;TZID=" + ZONE + ":20260202T090000\r\n");
		String blob = stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_SINGLE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("0105", "0126", "0209"), days(result));
		assertEquals(3, result.getMasterChild().getProperties(PropertyName.EXDATE).size());
	}

	@Test
	public void deletingThisAndFutureEndsTheRule() throws Exception {
		String calendar = weekly("");
		String blob = stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_THIS_FUTURE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("0105", "0112"), days(result));
		String rrule = result.getMasterChild().getRRule();
		assertTrue("Rule should end with UNTIL: " + rrule, rrule.contains("UNTIL="));
		assertTrue("Rule should no longer have COUNT: " + rrule, !rrule.contains("COUNT="));
	}

	/**
	 * This used to fail without saying so when the instance chosen was one
	 * that had been changed, because such an instance has no rule of its own.
	 */
	@Test
	public void deletingThisAndFutureFromAChangedInstance() throws Exception {
		String calendar = weekly("", override("20260119T090000", "20260119T140000", "Moved to the afternoon"));
		String blob = stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_THIS_FUTURE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("0105", "0112"), days(result));
		assertEquals(Arrays.asList("Weekly"), summaries(result));
	}

	/**
	 * Changed instances after the cut-off used to be left in the calendar,
	 * and came back the next time it was synchronised.
	 */
	@Test
	public void deletingThisAndFutureRemovesLaterChangedInstancesOnly() throws Exception {
		String calendar = weekly("",
				override("20260112T090000", "20260112T140000", "Earlier change"),
				override("20260202T090000", "20260202T140000", "Later change"));
		String blob = stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_THIS_FUTURE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("0105", "0112"), days(result));
		assertEquals(Arrays.asList("Weekly", "Earlier change"), summaries(result));
	}

	@Test
	public void deletingAllInstancesMeansDeletingTheResource() throws Exception {
		String calendar = weekly("");
		assertNull(stored(calendar).applyEventAction(instanceAt(calendar, "20260119T090000"),
				EventEdit.ACTION_DELETE, EventEdit.INSTANCES_ALL));
	}

	@Ignore("#56: the master event is lost")
	@Test
	public void editingOneInstanceAddsAChangedInstance() throws Exception {
		String calendar = weekly("");
		EventInstance instance = instanceAt(calendar, "20260119T090000");
		instance.setSummary("Just this once");
		String blob = stored(calendar).applyEventAction(instance, EventEdit.ACTION_EDIT, EventEdit.INSTANCES_SINGLE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("Weekly", "Just this once"), summaries(result));
		assertEquals(Arrays.asList("0105", "0112", "0119", "0126", "0202", "0209"), days(result));

		RecurrenceId recurrenceId = RecurrenceId.fromString("RECURRENCE-ID;TZID=" + ZONE + ":20260119T090000");
		Masterable changed = result.getChildFromRecurrenceId(recurrenceId);
		assertEquals("Just this once", changed.getSummary());
		assertEquals("20260119T090000", changed.getRecurrenceId().getValue());
		assertTrue(!changed.getRecurrenceId().isThisAndFuture());
	}

	@Ignore("#56: the master event is lost")
	@Test
	public void editingThisAndFutureAddsAChangedInstanceWithARange() throws Exception {
		String calendar = weekly("");
		EventInstance instance = instanceAt(calendar, "20260119T090000");
		instance.setSummary("From now on");
		String blob = stored(calendar).applyEventAction(instance, EventEdit.ACTION_EDIT, EventEdit.INSTANCES_THIS_FUTURE);

		VCalendar result = parse(blob);
		assertEquals(Arrays.asList("Weekly", "From now on"), summaries(result));

		// The instance asked for, and a later one, both get the change
		for( String when : new String[] { "20260119T090000", "20260202T090000" } ) {
			Masterable changed = result.getChildFromRecurrenceId(
					RecurrenceId.fromString("RECURRENCE-ID;TZID=" + ZONE + ":" + when));
			assertEquals(when, "From now on", changed.getSummary());
		}
		// An earlier one does not
		assertEquals("Weekly", result.getChildFromRecurrenceId(
				RecurrenceId.fromString("RECURRENCE-ID;TZID=" + ZONE + ":20260112T090000")).getSummary());
	}

	@Test
	public void editingAllInstancesChangesTheMaster() throws Exception {
		String calendar = weekly("");
		EventInstance instance = instanceAt(calendar, "20260119T090000");
		instance.setSummary("Renamed");
		String blob = stored(calendar).applyEventAction(instance, EventEdit.ACTION_EDIT, EventEdit.INSTANCES_ALL);

		assertEquals(Arrays.asList("Renamed"), summaries(parse(blob)));
	}
}
