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

import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class CalDavBackendTest {

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
