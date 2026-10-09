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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class VComponentTest {

	static final String RECURRING_EVENT =
			"BEGIN:VCALENDAR\r\n" +
			"VERSION:2.0\r\n" +
			"PRODID:-//aCal//test//EN\r\n" +
			"BEGIN:VEVENT\r\n" +
			"UID:recurring-1\r\n" +
			"DTSTAMP:20260101T000000Z\r\n" +
			"DTSTART;TZID=Pacific/Auckland:20260105T090000\r\n" +
			"DTEND;TZID=Pacific/Auckland:20260105T100000\r\n" +
			"SUMMARY:Stand-up\\, daily\r\n" +
			"DESCRIPTION:A description long enough that it has to be folded across mor\r\n" +
			" e than one line of the file.\r\n" +
			"RRULE:FREQ=DAILY;COUNT=5\r\n" +
			"EXDATE;TZID=Pacific/Auckland:20260106T090000\r\n" +
			"EXDATE;TZID=Pacific/Auckland:20260108T090000\r\n" +
			"END:VEVENT\r\n" +
			"END:VCALENDAR\r\n";

	private static VCalendar parse(String blob) {
		VComponent component = VComponent.createComponentFromBlob(blob);
		assertTrue("Expected a VCalendar", component instanceof VCalendar);
		return (VCalendar) component;
	}

	private static List<String> values(List<AcalProperty> properties) {
		List<String> values = new ArrayList<String>();
		for( AcalProperty property : properties ) values.add(property.getValue());
		return values;
	}

	@Test
	public void parsesCalendarWithOneEvent() {
		VCalendar calendar = parse(RECURRING_EVENT);
		assertEquals(1, calendar.getChildren().size());
		assertEquals("2.0", calendar.safePropertyValue(PropertyName.VERSION));

		Masterable event = calendar.getMasterChild();
		assertNotNull(event);
		assertTrue(event instanceof VEvent);
		assertEquals("recurring-1", event.safePropertyValue(PropertyName.UID));
		assertEquals("FREQ=DAILY;COUNT=5", event.safePropertyValue(PropertyName.RRULE));
	}

	@Test
	public void unescapesTextValues() {
		assertEquals("Stand-up, daily", parse(RECURRING_EVENT).getMasterChild().safePropertyValue(PropertyName.SUMMARY));
	}

	@Test
	public void unfoldsLongLines() {
		assertEquals("A description long enough that it has to be folded across more than one line of the file.",
				parse(RECURRING_EVENT).getMasterChild().safePropertyValue(PropertyName.DESCRIPTION));
	}

	@Test
	public void keepsParameters() {
		AcalProperty dtStart = parse(RECURRING_EVENT).getMasterChild().getProperty(PropertyName.DTSTART);
		assertEquals("20260105T090000", dtStart.getValue());
		assertEquals("Pacific/Auckland", dtStart.getParam("TZID"));
	}

	/**
	 * Only the last of several EXDATE lines used to be kept, which is how
	 * Google and Thunderbird write them.
	 */
	@Test
	public void keepsEveryLineOfARepeatedProperty() {
		List<AcalProperty> exDates = parse(RECURRING_EVENT).getMasterChild().getProperties(PropertyName.EXDATE);
		List<String> expected = new ArrayList<String>();
		expected.add("20260106T090000");
		expected.add("20260108T090000");
		assertEquals(expected, values(exDates));
	}

	@Test
	public void writesBackWhatItRead() {
		Masterable original = parse(RECURRING_EVENT).getMasterChild();
		Masterable written = parse(parse(RECURRING_EVENT).getCurrentBlob()).getMasterChild();

		PropertyName[] single = { PropertyName.UID, PropertyName.DTSTAMP, PropertyName.DTSTART, PropertyName.DTEND,
				PropertyName.SUMMARY, PropertyName.DESCRIPTION, PropertyName.RRULE };
		for( PropertyName name : single ) {
			assertEquals(name.toString(), original.safePropertyValue(name), written.safePropertyValue(name));
		}
		assertEquals(values(original.getProperties(PropertyName.EXDATE)),
				values(written.getProperties(PropertyName.EXDATE)));
		assertEquals("Pacific/Auckland", written.getProperty(PropertyName.DTSTART).getParam("TZID"));
	}
}
