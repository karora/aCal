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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AcalDateTimeTest {

	private static final long HOUR_MS = 3600 * 1000L;

	@Test
	public void parsesUtcDateTime() {
		AcalDateTime utc = AcalDateTime.fromString("20260115T103000Z");
		assertEquals(1768473000L, utc.getEpoch());
		assertEquals("UTC", utc.getTimeZoneId());
		assertEquals("20260115T103000Z", utc.fmtIcal());
		assertFalse(utc.isDate());
		assertFalse(utc.isFloating());
	}

	@Test
	public void parsesIsoFormatWithSeparators() {
		assertEquals(AcalDateTime.fromString("20260115T103000Z").getMillis(),
				AcalDateTime.fromString("2026-01-15 10:30:00Z").getMillis());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnrecognisedDateString() {
		AcalDateTime.fromString("next Tuesday");
	}

	@Test
	public void keepsWallClockTimeInNamedZone() {
		AcalDateTime local = AcalDateTime.fromIcalendar("20260115T103000", null, "Pacific/Auckland");
		assertEquals("Pacific/Auckland", local.getTimeZoneId());
		assertEquals("20260115T103000", local.fmtIcal());
		// Auckland is 13 hours ahead of UTC in January
		assertEquals(AcalDateTime.fromString("20260114T213000Z").getMillis(), local.getMillis());
	}

	@Test
	public void givesDateFields() {
		AcalDateTime utc = AcalDateTime.fromString("20260115T103000Z");
		assertEquals(2026, utc.getYear());
		assertEquals(AcalDateTime.JANUARY, utc.getMonth());
		assertEquals(15, utc.getMonthDay());
		assertEquals(10, utc.getHour());
		assertEquals(30, utc.getMinute());
		assertEquals(0, utc.getSecond());
		assertEquals(AcalDateTime.THURSDAY, utc.getWeekDay());
		assertEquals(15, utc.getYearDay());
	}

	@Test
	public void knowsMonthLengths() {
		assertEquals(31, AcalDateTime.monthDays(2026, 1));
		assertEquals(28, AcalDateTime.monthDays(2026, 2));
		assertEquals(29, AcalDateTime.monthDays(2024, 2));
		assertEquals(29, AcalDateTime.monthDays(2000, 2));
		assertEquals(28, AcalDateTime.monthDays(1900, 2));
		assertEquals(30, AcalDateTime.monthDays(2026, 4));
	}

	@Test
	public void addsDaysAcrossLeapDay() {
		AcalDateTime start = AcalDateTime.fromString("20240228T120000Z");
		assertEquals("20240229T120000Z", AcalDateTime.addDays(start, 1).fmtIcal());
		assertEquals("20240301T120000Z", AcalDateTime.addDays(start, 2).fmtIcal());
		assertEquals("20240227T120000Z", AcalDateTime.addDays(start, -1).fmtIcal());
		// The original is not changed
		assertEquals("20240228T120000Z", start.fmtIcal());
	}

	@Test
	public void addingADayKeepsWallClockTimeAcrossEndOfDaylightSaving() {
		// Daylight saving in Auckland ended at 3am on Sunday 5 April 2026
		AcalDateTime before = AcalDateTime.fromIcalendar("20260404T090000", null, "Pacific/Auckland");
		AcalDateTime after = AcalDateTime.addDays(before, 1);
		assertEquals("20260405T090000", after.fmtIcal());
		assertEquals(25 * HOUR_MS, after.getMillis() - before.getMillis());
	}

	@Test
	public void comparesInstantsNotWallClockTimes() {
		AcalDateTime auckland = AcalDateTime.fromIcalendar("20260115T103000", null, "Pacific/Auckland");
		AcalDateTime utc = AcalDateTime.fromString("20260115T000000Z");
		assertTrue(auckland.before(utc));
		assertTrue(utc.after(auckland));
	}
}
