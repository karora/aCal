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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class AcalPropertyTest {

	private static String repeat(String text, int times) {
		StringBuilder repeated = new StringBuilder();
		for( int i = 0; i < times; i++ ) repeated.append(text);
		return repeated.toString();
	}

	private static String unfold(String folded) {
		return folded.replace("\r\n ", "");
	}

	private static void assertFoldedTo(String folded, int maxOctets) {
		for( String line : folded.split("\r\n") ) {
			byte[] octets = line.getBytes(StandardCharsets.UTF_8);
			assertTrue("Line is " + octets.length + " octets: " + line, octets.length <= maxOctets);
			// Folding inside a character leaves something that is not valid UTF-8
			assertEquals(line, new String(octets, StandardCharsets.UTF_8));
			assertTrue("Line has a broken character: " + line, !line.contains("�"));
		}
	}

	@Test
	public void shortLinesAreNotFolded() {
		assertEquals("SUMMARY:Short", AcalProperty.rfc5545Wrap("SUMMARY:Short", 72));
	}

	@Test
	public void foldsEveryLineNotOnlyTheFirst() {
		String line = "DESCRIPTION:" + repeat("0123456789", 30);
		String folded = AcalProperty.rfc5545Wrap(line, 72);
		assertFoldedTo(folded, 72);
		assertEquals(line, unfold(folded));
		assertEquals(5, folded.split("\r\n").length);
	}

	@Test
	public void neverFoldsInsideACharacter() {
		// Two, three and four octet characters, at every possible offset from the fold
		for( String character : new String[] { "ā", "あ", "🗓" } ) {
			for( int offset = 0; offset < 4; offset++ ) {
				String line = "X:" + repeat("a", offset) + repeat(character, 100);
				String folded = AcalProperty.rfc5545Wrap(line, 72);
				assertFoldedTo(folded, 72);
				assertEquals(line, unfold(folded));
			}
		}
	}

	/**
	 * A long value ending in characters of more than one octet used to throw,
	 * and the event it belonged to was then left out of what was written.
	 */
	@Test
	public void longTextEndingInMultiOctetCharactersIsWritten() {
		String description = repeat("Kia ora koutou katoa. ", 5) + repeat("āēīōū ", 12);
		String blob = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:long-text\r\n"
				+ "DESCRIPTION:" + description + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

		VComponent calendar = VComponent.createComponentFromBlob(blob);
		calendar.setEditable();
		String written = calendar.getCurrentBlob();

		assertTrue("The event is missing from what was written:\n" + written, written.contains("BEGIN:VEVENT"));
		assertFoldedTo(written, 75);
		VCalendar reread = (VCalendar) VComponent.createComponentFromBlob(written);
		assertEquals(description, reread.getMasterChild().safePropertyValue(PropertyName.DESCRIPTION));
	}

	@Test
	public void readingTakesTheBackslashesOut() {
		assertEquals("One; two, three\nfour \\ five",
				AcalProperty.fromString("DESCRIPTION:One\\; two\\, three\\nfour \\\\ five").getValue());
		assertEquals("Line one\nLine two", AcalProperty.fromString("DESCRIPTION:Line one\\NLine two").getValue());
	}

	/**
	 * A backslash that was itself escaped is only a backslash.  With an n after
	 * it, it used to be taken for a new line.
	 */
	@Test
	public void anEscapedBackslashBeforeAnNIsNotANewLine() {
		assertEquals("C:\\new folder", AcalProperty.fromString("DESCRIPTION:C:\\\\new folder").getValue());
	}

	/** Text typed in aCal gets the backslashes RFC 5545 asks for, which a semicolon used not to (#53). */
	@Test
	public void writingTextPutsTheBackslashesIn() {
		assertEquals("SUMMARY:One\\; two\\, three\\nfour \\\\ five",
				new AcalProperty(PropertyName.SUMMARY, "One; two, three\nfour \\ five").toRfcString());
		assertEquals("One; two, three\nfour \\ five", AcalProperty.fromString(
				new AcalProperty(PropertyName.SUMMARY, "One; two, three\nfour \\ five").toRfcString()).getValue());
	}

	/** A value which is not text is written as it is. */
	@Test
	public void writingARuleLeavesItsCommasAndSemicolonsAlone() {
		assertEquals("RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
				new AcalProperty(PropertyName.RRULE, "FREQ=WEEKLY;BYDAY=MO,WE").toRfcString());
	}

	/**
	 * What was read is written back as it was, whatever kind of value it is:
	 * the commas here are not part of any text (#54).
	 */
	@Test
	public void aPropertyThatWasReadIsWrittenBackUnchanged() {
		for( String line : new String[] {
				"CATEGORIES:Work,Meetings",
				"CATEGORIES:Work\\, and play,Meetings",
				"X-APPLE-STRUCTURED-LOCATION;VALUE=URI:geo:-41.290450,174.782100",
				"REQUEST-STATUS:2.0;Success",
				"DESCRIPTION:One\\; two\\, three\\nfour",
				"SUMMARY:" } ) {
			assertEquals(line, AcalProperty.fromString(line).toRfcString());
		}
	}

	@Test
	public void changingAParameterLeavesTheValueAsItWasRead() {
		AcalProperty property = AcalProperty.fromString("CATEGORIES:Work,Meetings");
		property.setParam("LANGUAGE", "en");
		assertEquals("CATEGORIES;LANGUAGE=en:Work,Meetings", property.toRfcString());
	}
}
