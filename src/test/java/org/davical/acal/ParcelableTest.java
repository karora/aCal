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


package org.davical.acal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.acaltime.AcalDuration;
import org.davical.acal.davacal.PropertyName;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Objects that are passed between activities and to the service have to come
 * out of a Parcel the same as they went in.
 */
@RunWith(RobolectricTestRunner.class)
public class ParcelableTest {

	private static <T extends Parcelable> T throughAParcel(T original, Parcelable.Creator<T> creator) {
		Parcel parcel = Parcel.obtain();
		try {
			original.writeToParcel(parcel, 0);
			parcel.setDataPosition(0);
			T copy = creator.createFromParcel(parcel);
			assertEquals("Not everything written to the parcel was read back", 0, parcel.dataAvail());
			return copy;
		}
		finally {
			parcel.recycle();
		}
	}

	private static void assertSameDateTime(AcalDateTime expected, AcalDateTime actual) {
		assertEquals(expected.fmtIcal(), actual.fmtIcal());
		assertEquals(expected.getTimeZoneId(), actual.getTimeZoneId());
		assertEquals(expected.isDate(), actual.isDate());
		assertEquals(expected.isFloating(), actual.isFloating());
		assertEquals(expected.getMillis(), actual.getMillis());
	}

	@Test
	public void dateTimeInAZone() {
		AcalDateTime original = AcalDateTime.fromIcalendar("20260115T103000", null, "Pacific/Auckland");
		assertSameDateTime(original, throughAParcel(original, AcalDateTime.CREATOR));
	}

	@Test
	public void dateTimeInUtc() {
		AcalDateTime original = AcalDateTime.fromString("20260115T103000Z");
		assertSameDateTime(original, throughAParcel(original, AcalDateTime.CREATOR));
	}

	@Test
	public void floatingDateTime() {
		AcalDateTime original = AcalDateTime.fromIcalendar("20260115T103000", null, null);
		assertTrue(original.isFloating());
		assertSameDateTime(original, throughAParcel(original, AcalDateTime.CREATOR));
	}

	@Test
	public void dateWithoutATime() {
		AcalDateTime original = AcalDateTime.fromIcalendar("20260115", "DATE", null);
		assertTrue(original.isDate());
		assertSameDateTime(original, throughAParcel(original, AcalDateTime.CREATOR));
	}

	@Test
	public void duration() {
		for( String text : new String[] { "PT1H", "-PT15M", "P1DT2H", "P2W", "PT0S" } ) {
			AcalDuration original = new AcalDuration(text);
			AcalDuration copy = throughAParcel(original, AcalDuration.CREATOR);
			assertEquals(text, original.getDurationMillis(), copy.getDurationMillis());
			assertEquals(text, original.toString(), copy.toString());
		}
	}

	@Test
	public void dateRange() {
		AcalDateRange original = new AcalDateRange(
				AcalDateTime.fromIcalendar("20260115T103000", null, "Pacific/Auckland"),
				AcalDateTime.fromIcalendar("20260116T103000", null, "Pacific/Auckland"));
		AcalDateRange copy = throughAParcel(original, AcalDateRange.CREATOR);
		assertSameDateTime(original.start, copy.start);
		assertSameDateTime(original.end, copy.end);
	}

	@Test
	public void dateRangeWithNoEnd() {
		AcalDateRange original = new AcalDateRange(AcalDateTime.fromString("20260115T103000Z"), null);
		AcalDateRange copy = throughAParcel(original, AcalDateRange.CREATOR);
		assertSameDateTime(original.start, copy.start);
		assertEquals(null, copy.end);
	}

	@Test
	public void calendar() {
		String description = "A description that is long enough to have to be folded when the event is written out, "
				+ "with some characters of more than one octet: āēīōū āēīōū āēīōū āēīōū";
		String blob = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:parcel-1\r\n"
				+ "DTSTART;TZID=Pacific/Auckland:20260105T090000\r\nDURATION:PT1H\r\nSUMMARY:Parcelled\r\n"
				+ "DESCRIPTION:" + description + "\r\nRRULE:FREQ=WEEKLY;COUNT=6\r\n"
				+ "EXDATE;TZID=Pacific/Auckland:20260112T090000\r\nEXDATE;TZID=Pacific/Auckland:20260119T090000\r\n"
				+ "END:VEVENT\r\nEND:VCALENDAR\r\n";
		VCalendar original = (VCalendar) VComponent.createComponentFromBlob(blob);
		VCalendar copy = throughAParcel(original, VCalendar.CREATOR);

		assertEquals("Parcelled", copy.getMasterChild().getSummary());
		assertEquals(description, copy.getMasterChild().safePropertyValue(PropertyName.DESCRIPTION));
		assertEquals("FREQ=WEEKLY;COUNT=6", copy.getMasterChild().getRRule());
		assertEquals(2, copy.getMasterChild().getProperties(PropertyName.EXDATE).size());
		assertEquals("20260105T090000", copy.getMasterChild().getStart().fmtIcal());
	}
}
