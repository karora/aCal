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


package org.davical.acal.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.davical.acal.xml.DavParserFactory.PARSEMETHOD;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Reading a WebDAV multistatus response, with each of the two parsers.
 * The response in src/test/resources/dav is written by hand in the form
 * DAViCal uses for a sync-collection REPORT.
 */
@RunWith(Parameterized.class)
public class DavNodeTest {

	@Parameters(name = "{0}")
	public static Collection<Object[]> parsers() {
		return Arrays.asList(new Object[][] { { PARSEMETHOD.SAX }, { PARSEMETHOD.DOM } });
	}

	private final PARSEMETHOD parser;

	public DavNodeTest(PARSEMETHOD parser) {
		this.parser = parser;
	}

	private DavNode parse(String xml) {
		return DavParserFactory.buildTreeFromXml(parser, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	private DavNode syncCollection() {
		InputStream in = DavNodeTest.class.getResourceAsStream("/dav/sync-collection.xml");
		assertNotNull(in);
		DavNode root = DavParserFactory.buildTreeFromXml(parser, in);
		assertNotNull(root);
		return root;
	}

	@Test
	public void findsEveryResponse() {
		assertEquals(3, syncCollection().getNodesFromPath("multistatus/response").size());
	}

	@Test
	public void findsNodesAtAnyDepthOfThePath() {
		DavNode root = syncCollection();
		assertEquals(3, root.getNodesFromPath("multistatus/response/href").size());
		assertEquals(2, root.getNodesFromPath("multistatus/response/propstat/prop/getetag").size());
		assertEquals(0, root.getNodesFromPath("multistatus/response/nothing-like-this").size());
	}

	@Test
	public void aStarMatchesAnyElement() {
		DavNode second = syncCollection().getNodesFromPath("multistatus/response").get(1);
		// getetag in the first propstat, calendar-data in the second
		assertEquals(2, second.getNodesFromPath("propstat/prop/*").size());
	}

	@Test
	public void readsText() {
		DavNode root = syncCollection();
		assertEquals("data:,42", root.getFirstNodeText("multistatus/sync-token"));

		DavNode first = root.getNodesFromPath("multistatus/response").get(0);
		assertEquals("/caldav.php/user1/home/first-event.ics", first.getFirstNodeText("href"));
		assertEquals("\"etag-one\"", first.getFirstNodeText("propstat/prop/getetag"));
		assertEquals("HTTP/1.1 200 OK", first.getFirstNodeText("propstat/status"));

		assertEquals("calendar-data", first.getNodesFromPath("propstat/prop/calendar-data").get(0).getTagName());
	}

	/** Only the DOM parser keeps namespaces, and it puts them in lower case. */
	@Test
	public void theDomParserKnowsNamespaces() {
		assumeTrue(parser == PARSEMETHOD.DOM);
		DavNode first = syncCollection().getNodesFromPath("multistatus/response").get(0);
		assertEquals("dav:", first.getNameSpace());
		assertEquals("urn:ietf:params:xml:ns:caldav",
				first.getNodesFromPath("propstat/prop/calendar-data").get(0).getNameSpace());
	}

	@Test
	public void decodesEntitiesAndKeepsLineBreaksInCalendarData() {
		String data = syncCollection().getNodesFromPath("multistatus/response").get(0)
				.getFirstNodeText("propstat/prop/calendar-data");
		assertTrue(data, data.contains("SUMMARY:Fish & chips <tonight>\n"));
		assertTrue(data, data.startsWith("BEGIN:VCALENDAR\n"));
	}

	@Test
	public void givesTheLastSegmentOfAnHref() {
		List<DavNode> responses = syncCollection().getNodesFromPath("multistatus/response");
		assertEquals("first-event.ics", responses.get(0).segmentFromFirstHref("href"));
		assertEquals("second%20event.ics", responses.get(1).segmentFromFirstHref("href"));
		assertNull(responses.get(0).segmentFromFirstHref("no-such-element"));
	}

	@Test
	public void aResponseCanHaveAStatusOfItsOwn() {
		DavNode deleted = syncCollection().getNodesFromPath("multistatus/response").get(2);
		assertEquals("HTTP/1.1 404 Not Found", deleted.getFirstNodeText("status"));
		assertEquals(0, deleted.getNodesFromPath("propstat").size());
	}

	@Test
	public void namespacePrefixesDoNotMatter() {
		DavNode root = parse("<D:multistatus xmlns:D=\"DAV:\"><D:response><D:href>/a</D:href></D:response></D:multistatus>");
		assertEquals("/a", root.getFirstNodeText("multistatus/response/href"));
	}

	@Test
	public void somethingThatIsNotXmlGivesNoTreeOrAnEmptyOne() {
		DavNode root = parse("This is not XML at all");
		assertTrue(root == null || root.getNodesFromPath("multistatus").isEmpty());
	}

	@Test
	public void xmlThatStopsHalfWayDoesNotThrow() {
		DavNode root = parse("<multistatus xmlns=\"DAV:\"><response><href>/a</href>");
		assertTrue(root == null || root.getNodesFromPath("multistatus/response").size() <= 1);
	}
}
