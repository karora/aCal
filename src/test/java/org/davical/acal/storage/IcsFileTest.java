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

package org.davical.acal.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;
import org.davical.acal.storage.IcsFile.Item;
import org.junit.Test;

public class IcsFileTest {

	private static final String ZONE =
			"BEGIN:VTIMEZONE\r\nTZID:Pacific/Auckland\r\n"
			+ "BEGIN:STANDARD\r\nDTSTART:19700405T030000\r\nTZOFFSETFROM:+1300\r\nTZOFFSETTO:+1200\r\nEND:STANDARD\r\n"
			+ "END:VTIMEZONE\r\n";

	private static String calendar(String... components) {
		StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n");
		for ( String component : components ) out.append(component);
		return out.append("END:VCALENDAR\r\n").toString();
	}

	private static String event(String uid, String more) {
		return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n" + more + "END:VEVENT\r\n";
	}

	/** The lines of a calendar that are inside its events, tasks and notes. */
	private static List<String> itemLines(String calendar) {
		List<String> lines = new ArrayList<String>();
		int depth = 0;
		boolean keeping = false;
		for ( String line : calendar.split("\r?\n") ) {
			if ( line.startsWith("BEGIN:") ) {
				depth++;
				if ( depth == 2 ) keeping = !line.equals("BEGIN:VTIMEZONE");
			}
			if ( depth >= 2 && keeping ) lines.add(line);
			if ( line.startsWith("END:") ) depth--;
		}
		return lines;
	}

	private static List<File> fixtures() throws Exception {
		File[] files = new File(IcsFileTest.class.getResource("/ical").toURI()).listFiles();
		Arrays.sort(files);
		List<File> fixtures = new ArrayList<File>();
		for ( File file : files ) {
			if ( file.getName().endsWith(".ics") ) fixtures.add(file);
		}
		assertTrue("No fixtures found", fixtures.size() > 10);
		return fixtures;
	}

	private static String read(File file) throws Exception {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/**
	 * Every fixture is stored as a resource, the lot is exported to one file,
	 * and the file is imported: what comes out is what went in, line for line,
	 * with repeat rules, changed instances and alarms where they were.
	 */
	@Test
	public void exportingACollectionAndImportingTheFileGivesBackWhatWasInIt() throws Exception {
		List<String> resources = new ArrayList<String>();
		Map<String, List<String>> expected = new LinkedHashMap<String, List<String>>();
		for ( File fixture : fixtures() ) {
			String resource = read(fixture);
			List<Item> held = IcsFile.split(resource);
			if ( held.isEmpty() ) continue;		// not a calendar
			assertEquals(fixture.getName() + " holds more than one thing", 1, held.size());
			resources.add(resource);
			String key = held.get(0).type + " " + held.get(0).uid;
			assertFalse("Two fixtures are both " + key, expected.containsKey(key));
			expected.put(key, itemLines(resource));
		}
		assertTrue(expected.size() > 10);

		List<Item> imported = IcsFile.split(IcsFile.export(resources));

		assertEquals(expected.size(), imported.size());
		assertEquals(expected.size(), IcsFile.count(resources));
		for ( Item item : imported ) {
			String key = item.type + " " + item.uid;
			assertTrue("Unexpected " + key, expected.containsKey(key));
			assertEquals(key, expected.get(key), itemLines(item.data));
		}
	}

	@Test
	public void everythingImportedIsACalendarWeCanRead() throws Exception {
		List<String> resources = new ArrayList<String>();
		for ( File fixture : fixtures() ) resources.add(read(fixture));

		for ( Item item : IcsFile.split(IcsFile.export(resources)) ) {
			VComponent component = VComponent.createComponentFromBlob(item.data);
			assertTrue(item.uid, component instanceof VCalendar);
			assertNotNull(item.uid, ((VCalendar) component).getMasterChild());
		}
	}

	@Test
	public void aChangedInstanceStaysWithTheEventItBelongsTo() {
		String file = calendar(
				event("weekly", "RRULE:FREQ=WEEKLY\r\nDTSTART:20260105T090000Z\r\n"),
				event("other", "DTSTART:20260106T090000Z\r\n"),
				event("weekly", "RECURRENCE-ID:20260112T090000Z\r\nDTSTART:20260113T140000Z\r\n"));

		List<Item> items = IcsFile.split(file);

		assertEquals(2, items.size());
		assertEquals("weekly", items.get(0).uid);
		assertEquals("VEVENT", items.get(0).type);
		assertTrue(items.get(0).data.contains("RRULE:FREQ=WEEKLY"));
		assertTrue(items.get(0).data.contains("RECURRENCE-ID:20260112T090000Z"));
		assertEquals("other", items.get(1).uid);
		assertFalse(items.get(1).data.contains("RECURRENCE-ID"));
	}

	@Test
	public void eachTimezoneIsWrittenOnceAndGoesWithWhatMentionsIt() {
		String here = event("here", "DTSTART;TZID=Pacific/Auckland:20260105T090000\r\n");
		String there = event("there", "DTSTART;TZID=\"Pacific/Auckland\":20260106T090000\r\n");
		String utc = event("utc", "DTSTART:20260107T090000Z\r\n");

		String file = IcsFile.export(Arrays.asList(calendar(ZONE, here), calendar(ZONE, there), calendar(utc)));
		assertEquals(2, file.split("BEGIN:VTIMEZONE", -1).length);

		List<Item> items = IcsFile.split(file);
		assertEquals(3, items.size());
		assertTrue(items.get(0).data.contains("BEGIN:VTIMEZONE"));
		assertTrue(items.get(1).data.contains("BEGIN:VTIMEZONE"));
		assertFalse(items.get(2).data.contains("BEGIN:VTIMEZONE"));
	}

	@Test
	public void anAlarmStaysInsideItsEventAndItsUidIsNotTheEvents() {
		String file = calendar(event("the-event",
				"DTSTART:20260105T090000Z\r\nBEGIN:VALARM\r\nUID:the-alarm\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\n"));

		List<Item> items = IcsFile.split(file);

		assertEquals(1, items.size());
		assertEquals("the-event", items.get(0).uid);
		assertTrue(items.get(0).data.contains("BEGIN:VALARM\r\nUID:the-alarm\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nEND:VALARM"));
		assertEquals("the-event", IcsFile.uidOf(items.get(0).data));
	}

	@Test
	public void aUidCarriedOverOntoAnotherLineIsReadWhole() {
		String file = calendar("BEGIN:VEVENT\r\nUID:a-very-long-uid-that-was-fol\r\n ded-by-whatever-wrote-it\r\nDTSTART:20260105T090000Z\r\nEND:VEVENT\r\n");

		assertEquals("a-very-long-uid-that-was-folded-by-whatever-wrote-it", IcsFile.split(file).get(0).uid);
	}

	@Test
	public void somethingWithNoUidIsGivenOne() {
		String file = calendar("BEGIN:VTODO\r\nSUMMARY:One\r\nEND:VTODO\r\n", "BEGIN:VTODO\r\nSUMMARY:Two\r\nEND:VTODO\r\n");

		List<Item> items = IcsFile.split(file);

		assertEquals(2, items.size());
		assertNotNull(items.get(0).uid);
		assertFalse(items.get(0).uid.equals(items.get(1).uid));
		assertTrue(items.get(0).data.contains("UID:" + items.get(0).uid + "\r\nSUMMARY:One"));
	}

	@Test
	public void eventsTasksAndNotesAreTakenAndTheRestIsLeft() {
		String file = calendar(
				event("an-event", "DTSTART:20260105T090000Z\r\n"),
				"BEGIN:VTODO\r\nUID:a-task\r\nEND:VTODO\r\n",
				"BEGIN:VJOURNAL\r\nUID:a-note\r\nEND:VJOURNAL\r\n",
				"BEGIN:VFREEBUSY\r\nUID:busy\r\nEND:VFREEBUSY\r\n")
				+ "BEGIN:VCARD\r\nUID:a-contact\r\nFN:Ada\r\nEND:VCARD\r\n";

		List<Item> items = IcsFile.split(file);

		assertEquals(3, items.size());
		assertEquals("VEVENT", items.get(0).type);
		assertEquals("VTODO", items.get(1).type);
		assertEquals("VJOURNAL", items.get(2).type);
	}

	@Test
	public void aFileOfSeveralCalendarsIsReadAsOne() {
		String file = calendar(event("one", "")) + calendar(event("two", ""));

		assertEquals(2, IcsFile.split(file).size());
	}

	@Test
	public void lineEndingsOfAnyKindAreRead() {
		String file = calendar(event("one", "SUMMARY:First\r\n"), event("two", "")).replace("\r\n", "\n");

		List<Item> items = IcsFile.split(file);
		assertEquals(2, items.size());
		assertTrue(items.get(0).data.contains("UID:one\r\nDTSTAMP:20260101T000000Z\r\nSUMMARY:First\r\n"));
	}

	@Test
	public void whatIsNotACalendarHoldsNothing() {
		assertEquals(0, IcsFile.split("").size());
		assertEquals(0, IcsFile.split(null).size());
		assertEquals(0, IcsFile.split("<html>Not found</html>").size());
		assertNull(IcsFile.uidOf("BEGIN:VCARD\r\nUID:a-contact\r\nEND:VCARD\r\n"));
		assertEquals("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//morphoss.com//aCal 1.0//EN\r\nEND:VCALENDAR\r\n",
				IcsFile.export(new ArrayList<String>()));
	}
}
