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

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.Header;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class CalDavBackendTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

	private static String describe(Header[] headers) {
		StringBuilder described = new StringBuilder();
		for ( Header header : headers ) {
			described.append(header.getName()).append(": ").append(header.getValue()).append("\n");
		}
		return described.toString();
	}

	@Test
	public void aNewResourceIsSentOnlyIfNothingIsThere() {
		assertEquals("If-None-Match: *\n"
					+ "Content-type: text/calendar; charset=\"utf-8\"\n"
					+ "Prefer: return=representation\n",
					describe(CalDavBackend.headersFor(CalDavChange.plan(null, EVENT, null, "abc", null))));
	}

	@Test
	public void anUpdateIsSentOnlyIfTheEtagStillMatches() {
		assertEquals("If-Match: \"etag-1\"\n"
					+ "Content-type: text/calendar; charset=\"utf-8\"\n"
					+ "Prefer: return=representation\n",
					describe(CalDavBackend.headersFor(CalDavChange.plan(EVENT, EVENT, "abc.ics", "abc", "\"etag-1\""))));
	}

	@Test
	public void aDeleteAsksForNothingBack() {
		assertEquals("If-Match: \"etag-1\"\n"
					+ "Content-type: text/plain\n",
					describe(CalDavBackend.headersFor(CalDavChange.plan(EVENT, null, "abc.ics", "abc", "\"etag-1\""))));
	}

	/**
	 * There was a null in the place of the missing condition, which the
	 * request could not be built from, so the change was never sent and
	 * stayed waiting for good.
	 */
	@Test
	public void aChangeWithNoEtagHasNoHeaderWhereTheConditionWouldBe() {
		assertEquals("Content-type: text/calendar; charset=\"utf-8\"\n"
					+ "Prefer: return=representation\n",
					describe(CalDavBackend.headersFor(CalDavChange.plan(EVENT, EVENT, "abc.ics", "abc", null))));
		assertEquals("Content-type: text/plain\n",
					describe(CalDavBackend.headersFor(CalDavChange.plan(EVENT, null, "abc.ics", "abc", null))));
	}

	@Test
	public void ordinaryTextIsOneSection() {
		assertEquals("<![CDATA[Home & <Work>]]>", CalDavBackend.cdata("Home & <Work>"));
	}

	/** A name is whatever the user typed, and may not add properties of its own to the PROPPATCH. */
	@Test
	public void textCannotEndTheSectionAndCarryOnAsXml() throws Exception {
		for ( String name : new String[] { "Home]]></displayname><owner>x</owner><displayname><![CDATA[",
					"]]>", "a]]>b]]>c", "]]]]>>", "plain" } ) {
			Element prop = DocumentBuilderFactory.newInstance().newDocumentBuilder()
						.parse(new InputSource(new StringReader(
									"<prop><displayname>" + CalDavBackend.cdata(name) + "</displayname></prop>")))
						.getDocumentElement();

			assertEquals(name, 1, prop.getChildNodes().getLength());
			assertEquals(name, prop.getFirstChild().getTextContent());
		}
	}
}
