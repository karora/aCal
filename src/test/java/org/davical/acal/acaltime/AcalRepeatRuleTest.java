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

	/** The instances in 2026 of a floating event with only a rule, in iCalendar format. */
	private static List<String> floatingInstances(String dtStart, String rrule) {
		AcalRepeatRule rule = new AcalRepeatRule(AcalDateTime.fromString(dtStart), rrule);
		List<String> found = new ArrayList<String>();
		for( AcalDateTime instance : rule.getInstancesInRange(
				AcalDateTime.fromString("20260101T000000"), AcalDateTime.fromString("20270101T000000")) ) {
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
	 * UNTIL in UTC is a moment, and the instance at that moment is the last one.
	 * 9am on 8 January in Auckland is 8pm on the 7th in UTC.  It used to be read
	 * as 8pm on the 7th in Auckland, which lost the last instance (#47).
	 */
	@Test
	public void untilInUtcIsComparedAsAMoment() {
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000", "20260108T090000"),
				instances(event("RRULE:FREQ=DAILY;UNTIL=20260107T200000Z")));
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000"),
				instances(event("RRULE:FREQ=DAILY;UNTIL=20260107T195959Z")));
	}

	/** UNTIL with no zone is a clock time where the event is. */
	@Test
	public void floatingUntilIsComparedInTheZoneOfTheEvent() {
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000"),
				instances(event("RRULE:FREQ=DAILY;UNTIL=20260107T090000")));
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000"),
				instances(event("RRULE:FREQ=DAILY;UNTIL=20260107T085959")));
	}

	/** A floating event has no moment to compare with, so the clock times are compared. */
	@Test
	public void untilInUtcOnAFloatingEventIsComparedAsAClockTime() {
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000"),
				floatingInstances("20260105T090000", "FREQ=DAILY;UNTIL=20260107T235959Z"));
	}

	@Test
	public void untilIsWrittenBackAsItWasRead() {
		for( String rule : new String[] { "RRULE:FREQ=DAILY;UNTIL=20260107T200000Z",
				"RRULE:FREQ=DAILY;UNTIL=20260107T200000", "RRULE:FREQ=DAILY;UNTIL=20260107" } ) {
			AcalRepeatRuleParser parsed = AcalRepeatRuleParser.parseRepeatRule(rule);
			parsed.nextBaseDate(AcalDateTime.fromIcalendar("20260105T090000", null, ZONE));
			assertEquals(rule, parsed.toString());
		}
	}

	/** What the repeat rule editor writes for "ends on 14 January". */
	@Test
	public void untilForADayIsTheEndOfThatDayWhereTheEventIs() {
		// Auckland is 13 hours ahead of UTC in January
		assertEquals("20260114T105959Z", AcalRepeatRuleParser.untilForLastDay(
				AcalDateTime.fromIcalendar("20260105T090000", null, ZONE), 2026, 1, 14));
		assertEquals("20260114T235959Z", AcalRepeatRuleParser.untilForLastDay(
				AcalDateTime.fromString("20260105T090000Z"), 2026, 1, 14));
		assertEquals("20260114T235959", AcalRepeatRuleParser.untilForLastDay(
				AcalDateTime.fromString("20260105T090000"), 2026, 1, 14));
		assertEquals("20260114", AcalRepeatRuleParser.untilForLastDay(
				AcalDateTime.fromString("20260105"), 2026, 1, 14));
	}

	/** ...and what it shows for a rule it is given, which is the same day back again. */
	@Test
	public void untilDayIsTheDayWhereTheEventIs() {
		AcalDateTime auckland = AcalDateTime.fromIcalendar("20260105T090000", null, ZONE);
		assertEquals("20260114T000000", AcalRepeatRuleParser
				.parseRepeatRule("FREQ=DAILY;UNTIL=20260114T105959Z").getUntilDay(auckland).fmtIcal());
		// 8pm on the 13th in UTC is 9am on the 14th in Auckland
		assertEquals("20260114T000000", AcalRepeatRuleParser
				.parseRepeatRule("FREQ=DAILY;UNTIL=20260113T200000Z").getUntilDay(auckland).fmtIcal());
		assertEquals("20260114T000000", AcalRepeatRuleParser
				.parseRepeatRule("FREQ=DAILY;UNTIL=20260114").getUntilDay(AcalDateTime.fromString("20260105")).fmtIcal());
	}

	/** An event ending on a day in the editor has its last instance on that day. */
	@Test
	public void aRuleEndingOnADayIncludesThatDayAndNoLater() {
		String until = AcalRepeatRuleParser.untilForLastDay(
				AcalDateTime.fromIcalendar("20260105T090000", null, ZONE), 2026, 1, 7);
		assertEquals(Arrays.asList("20260105T090000", "20260106T090000", "20260107T090000"),
				instances(event("RRULE:FREQ=DAILY;UNTIL=" + until)));
	}

	/**
	 * With BYDAY it does not matter which day of the month DTSTART is on.  A
	 * month without that day, as February is without a 30th, used to be left
	 * out (#48).
	 */
	@Test
	public void monthlyByDayIncludesMonthsShorterThanTheDayOfDtStart() {
		assertEquals(Arrays.asList("20260130T090000", "20260227T090000", "20260327T090000", "20260424T090000"),
				floatingInstances("20260130T090000", "FREQ=MONTHLY;BYDAY=-1FR;COUNT=4"));
		// 30 January is not a first Friday, but DTSTART is always the first instance
		assertEquals(Arrays.asList("20260130T090000", "20260206T090000", "20260306T090000", "20260403T090000"),
				floatingInstances("20260130T090000", "FREQ=MONTHLY;BYDAY=1FR;COUNT=4"));
		assertEquals(Arrays.asList("20260131T090000", "20260203T090000", "20260210T090000", "20260217T090000",
						"20260224T090000", "20260303T090000"),
				floatingInstances("20260131T090000", "FREQ=MONTHLY;BYDAY=TU;COUNT=6"));
	}

	/** Without BYDAY, a month that has no such day still has no instance. */
	@Test
	public void monthlyOnThe30thStillSkipsFebruary() {
		assertEquals(Arrays.asList("20260130T090000", "20260330T090000", "20260430T090000"),
				floatingInstances("20260130T090000", "FREQ=MONTHLY;COUNT=3"));
	}

	/**
	 * An end a long way off is common, and one after 2038 used to be taken for
	 * a date before the event began, which left it with no instances (#49).
	 */
	@Test
	public void aRuleEndingAfter2038HasInstancesNow() {
		assertEquals(Arrays.asList("20261207T090000", "20261214T090000", "20261221T090000", "20261228T090000"),
				floatingInstances("20261207T090000", "FREQ=WEEKLY;UNTIL=20991231T235959Z"));
	}

	/** The first and the last weekday of each month, which Apple Calendar and Outlook both offer. */
	@Test
	public void bySetPosChoosesFromTheStartOrTheEndOfTheSet() {
		// 5 January is not the first weekday of the month, but DTSTART is always the first instance
		assertEquals(Arrays.asList("20260105T090000", "20260202T090000", "20260302T090000", "20260401T090000"),
				floatingInstances("20260105T090000", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1;COUNT=4"));
		assertEquals(Arrays.asList("20260105T090000", "20260130T090000", "20260227T090000", "20260331T090000"),
				floatingInstances("20260105T090000", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=4"));
	}

	/** Several positions give their instances in order of time, whatever order they are asked for in. */
	@Test
	public void bySetPosWithSeveralPositionsIsInOrderOfTime() {
		assertEquals(Arrays.asList("20260105T090000", "20260130T090000", "20260202T090000", "20260227T090000"),
				floatingInstances("20260105T090000", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1,1,-1;COUNT=4"));
	}

	/**
	 * Only four months of 2026 have a fifth Friday.  Asking for one in a month
	 * that has four used to throw IndexOutOfBoundsException (#50).
	 */
	@Test
	public void bySetPosBeyondTheSetChoosesNothing() {
		assertEquals(Arrays.asList("20260130T090000", "20260529T090000", "20260731T090000", "20261030T090000"),
				floatingInstances("20260130T090000", "FREQ=MONTHLY;BYDAY=FR;BYSETPOS=5"));
		assertEquals(Arrays.asList("20260102T090000", "20260501T090000", "20260703T090000", "20261002T090000"),
				floatingInstances("20260102T090000", "FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-5"));
	}

	/** A negative BYSETPOS used to be written one too high, so -1 became 0. */
	@Test
	public void bySetPosIsWrittenBackAsItWasRead() {
		String rule = "RRULE:FREQ=MONTHLY;BYSETPOS=-1,2;BYDAY=MO,TU";
		assertEquals(rule, AcalRepeatRuleParser.parseRepeatRule(rule).toString());
	}

	/** Without a rule, the instances are DTSTART and each RDATE.  Only DTSTART used to be found (#51). */
	@Test
	public void rdateWithoutARule() {
		assertEquals(Arrays.asList("20260105T090000", "20260108T140000", "20260220T090000"),
				instances(event("RDATE;TZID=" + ZONE + ":20260220T090000",
						"RDATE;TZID=" + ZONE + ":20260108T140000")));
	}

	/** An RDATE after the rule has ended used to be dropped, whether it ended by COUNT or by UNTIL (#51). */
	@Test
	public void rdateAfterTheRuleHasEnded() {
		List<String> expected = Arrays.asList("20260105T090000", "20260108T140000", "20260112T090000",
				"20260220T090000");
		String rdate = "RDATE;TZID=" + ZONE + ":20260108T140000,20260220T090000";
		assertEquals(expected, instances(event("RRULE:FREQ=WEEKLY;COUNT=2", rdate)));
		assertEquals(expected, instances(event("RRULE:FREQ=WEEKLY;UNTIL=20260112T090000", rdate)));
	}

	/** It has to be found as well when the period asked about starts after the rule has ended. */
	@Test
	public void rdateAfterUntilIsFoundInALaterPeriod() {
		VCalendar calendar = (VCalendar) VComponent.createComponentFromBlob(event(
				"RRULE:FREQ=WEEKLY;UNTIL=20260112T090000", "RDATE;TZID=" + ZONE + ":20260220T090000"));
		AcalRepeatRule rule = AcalRepeatRule.fromVCalendar(calendar, 1, 1);
		List<AcalDateTime> found = rule.getInstancesInRange(
				AcalDateTime.fromIcalendar("20260201T000000", null, ZONE),
				AcalDateTime.fromIcalendar("20260301T000000", null, ZONE));
		assertEquals(1, found.size());
		assertEquals("20260220T090000", found.get(0).fmtIcal());

		// The event lasts an hour, so everything is over at 10am on 20 February
		assertEquals("20260220T100000", calendar.getInstancesRange().end.fmtIcal());
	}

	/** An RDATE at the time of DTSTART, or of an instance the rule gives, is that instance and not another. */
	@Test
	public void rdateAtTheTimeOfAnInstanceIsNotASecondOne() {
		assertEquals(Arrays.asList("20260105T090000", "20260112T090000", "20260114T090000", "20260119T090000"),
				instances(event("RRULE:FREQ=WEEKLY;COUNT=3",
						"RDATE;TZID=" + ZONE + ":20260105T090000,20260112T090000,20260114T090000,20260114T090000",
						"RDATE:20260118T200000Z")));
	}

	/** EXDATE removes an RDATE as it does any other instance. */
	@Test
	public void excludedRdateIsLeftOut() {
		assertEquals(Arrays.asList("20260105T090000", "20260220T090000"),
				instances(event("RDATE;TZID=" + ZONE + ":20260108T140000,20260220T090000",
						"EXDATE;TZID=" + ZONE + ":20260108T140000")));
	}

	/** A rule more frequent than daily used not to be read at all, and the event had no instances (#52). */
	@Test
	public void hourlyMinutelyAndSecondly() {
		assertEquals(Arrays.asList("20260105T090000", "20260105T150000", "20260105T210000", "20260106T030000"),
				floatingInstances("20260105T090000", "FREQ=HOURLY;INTERVAL=6;COUNT=4"));
		assertEquals(Arrays.asList("20260105T090000", "20260105T091500", "20260105T093000", "20260105T094500"),
				floatingInstances("20260105T090000", "FREQ=MINUTELY;INTERVAL=15;COUNT=4"));
		assertEquals(Arrays.asList("20260105T090000", "20260105T090030", "20260105T090100"),
				floatingInstances("20260105T090000", "FREQ=SECONDLY;INTERVAL=30;COUNT=3"));
	}

	/**
	 * Every two hours, on Mondays.  The six days between are more steps with
	 * nothing in them than a rule is allowed at the longer frequencies.
	 */
	@Test
	public void hourlyOnOneDayOfTheWeekCarriesOnToTheNextWeek() {
		assertEquals(Arrays.asList("20260105T090000", "20260105T110000", "20260105T130000", "20260105T150000",
						"20260105T170000", "20260105T190000", "20260105T210000", "20260105T230000",
						"20260112T010000", "20260112T030000"),
				floatingInstances("20260105T090000", "FREQ=HOURLY;INTERVAL=2;BYDAY=MO;COUNT=10"));
	}

	/** BYHOUR chooses among the instances of a rule by the minute, and BYMINUTE adds to those of one by the hour. */
	@Test
	public void byHourAndByMinuteWithTheShortFrequencies() {
		assertEquals(Arrays.asList("20260105T090000", "20260105T093000", "20260105T100000", "20260105T103000",
						"20260106T090000", "20260106T093000"),
				floatingInstances("20260105T090000", "FREQ=MINUTELY;INTERVAL=30;BYHOUR=9,10;COUNT=6"));
		assertEquals(Arrays.asList("20260105T090000", "20260105T093000", "20260105T100000", "20260105T103000"),
				floatingInstances("20260105T090000", "FREQ=HOURLY;BYMINUTE=0,30;COUNT=4"));
	}

	/** BYHOUR used to be ignored unless the rule had a BYDAY as well. */
	@Test
	public void byHourAddsInstancesToADailyRule() {
		assertEquals(Arrays.asList("20260105T090000", "20260105T170000", "20260106T090000", "20260106T170000"),
				floatingInstances("20260105T090000", "FREQ=DAILY;BYHOUR=9,17;COUNT=4"));
	}

	/**
	 * A rule is written by whoever wrote the calendar.  Repeating a value must
	 * not repeat the instance, and a value that is no hour at all is left out.
	 */
	@Test
	public void repeatedAndImpossibleTimesInARuleAreLeftOut() {
		assertEquals(Arrays.asList("20260105T090000", "20260105T170000", "20260106T090000", "20260106T170000"),
				floatingInstances("20260105T090000", "FREQ=DAILY;BYHOUR=17,9,9,24,-1,x,17;COUNT=4"));
		assertEquals("RRULE:FREQ=DAILY;BYHOUR=9,17",
				AcalRepeatRuleParser.parseRepeatRule("FREQ=DAILY;BYHOUR=17,9,9,24,-1,x,17").toString());
	}

	/** Every second of every day, from a daily rule, is more than one step may hold. */
	@Test(expected = IllegalStateException.class)
	public void aRuleMakingTooManyInstancesOfOneStepIsRefused() {
		StringBuilder all = new StringBuilder();
		for( int i = 0; i < 60; i++ ) all.append(i == 0 ? "" : ",").append(i);
		floatingInstances("20260105T090000", "FREQ=DAILY;BYHOUR=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,"
				+ "20,21,22,23;BYMINUTE=" + all + ";BYSECOND=" + all);
	}

	@Test
	public void theShortFrequenciesAreWrittenBackAsTheyWereRead() {
		for( String rule : new String[] { "RRULE:FREQ=HOURLY;INTERVAL=6", "RRULE:FREQ=MINUTELY;COUNT=3",
				"RRULE:FREQ=SECONDLY;INTERVAL=30" } ) {
			assertEquals(rule, AcalRepeatRuleParser.parseRepeatRule(rule).toString());
		}
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
