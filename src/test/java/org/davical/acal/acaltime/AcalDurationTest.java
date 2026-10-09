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

import org.junit.Test;

public class AcalDurationTest {

	@Test
	public void parsesNegativeMinutes() {
		AcalDuration duration = new AcalDuration("-PT15M");
		assertEquals(-15 * 60 * 1000L, duration.getDurationMillis());
		assertEquals("-PT15M", duration.toString());
	}

	@Test
	public void parsesDaysAndHours() {
		AcalDuration duration = new AcalDuration("P1DT2H");
		assertEquals(26 * 3600 * 1000L, duration.getDurationMillis());
		assertEquals(1, duration.getDays());
		assertEquals("P1DT2H", duration.toString());
	}

	@Test
	public void parsesWeeks() {
		AcalDuration duration = new AcalDuration("P2W");
		assertEquals(14, duration.getDays());
		assertEquals("P2W", duration.toString());
	}

	@Test
	public void endDateIsStartPlusDuration() {
		AcalDateTime start = AcalDateTime.fromString("20260115T103000Z");
		assertEquals("20260116T123000Z", new AcalDuration("P1DT2H").getEndDate(start).fmtIcal());
	}
}
