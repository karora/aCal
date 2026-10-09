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
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.acaltime.AcalRepeatRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Runs every iCalendar file in src/test/resources/ical through the parser,
 * the serialiser and the recurrence engine.  To add a case, add a file: see
 * the README there.
 */
@RunWith(Parameterized.class)
public class ICalendarFixturesTest {

	private static final String FIXTURE_DIRECTORY = "/ical";
	private static final DateTimeFormatter UTC_FORMAT =
			DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

	@Parameters(name = "{0}")
	public static Collection<Object[]> fixtures() throws URISyntaxException {
		URL directory = ICalendarFixturesTest.class.getResource(FIXTURE_DIRECTORY);
		assertNotNull("Fixture directory is missing from the test resources", directory);
		File[] files = new File(directory.toURI()).listFiles();
		assertNotNull("Fixture directory cannot be listed: " + directory, files);
		Arrays.sort(files);

		List<Object[]> fixtures = new ArrayList<Object[]>();
		for( File file : files ) {
			String name = file.getName();
			if ( name.endsWith(".ics") ) fixtures.add(new Object[] { name.substring(0, name.length() - 4), file });
		}
		assertTrue("No fixtures found in " + directory, !fixtures.isEmpty());
		return fixtures;
	}

	private final String name;
	private final File icsFile;

	public ICalendarFixturesTest(String name, File icsFile) {
		this.name = name;
		this.icsFile = icsFile;
	}

	private interface Check {
		void run() throws IOException;
	}

	/**
	 * Runs a check, unless known-failures.txt says that it is known to fail.
	 * In that case it is skipped for as long as it does fail, and becomes a
	 * failure once it passes, so that the list has to be kept up to date.
	 */
	private void check(String checkName, Check check) throws IOException {
		String issue = knownFailures().get(name + " " + checkName);
		if ( issue == null ) {
			check.run();
			return;
		}
		try {
			check.run();
		}
		catch( AssertionError | RuntimeException expected ) {
			assumeTrue("Known to fail: see " + issue, false);
		}
		fail("This is listed in known-failures.txt for " + issue + ", but it passes now: remove it from the list");
	}

	private Map<String,String> knownFailures() throws IOException {
		Map<String,String> known = new HashMap<String,String>();
		for( String line : read(new File(icsFile.getParentFile(), "known-failures.txt")).split("\n") ) {
			if ( line.isEmpty() || line.startsWith("#") ) continue;
			String[] fields = line.trim().split("\\s+");
			assertEquals("Malformed line in known-failures.txt: " + line, 3, fields.length);
			known.put(fields[0] + " " + fields[1], fields[2]);
		}
		return known;
	}

	private static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private File sibling(String extension) {
		String path = icsFile.getPath();
		return new File(path.substring(0, path.length() - 4) + extension);
	}

	/**
	 * Parsing a file and writing it out again must not lose or change
	 * anything, including properties that aCal knows nothing about.  The order
	 * of the properties within a component is allowed to change.
	 */
	@Test
	public void survivesBeingReadAndWrittenBack() throws IOException {
		check("round-trip", new Check() {
			public void run() throws IOException {
				String original = read(icsFile);
				VComponent component = VComponent.createComponentFromBlob(original);
				assertNotNull("Could not be parsed", component);
				component.setEditable();
				String written = component.getCurrentBlob();

				assertEquals(canonical(original), canonical(written));
			}
		});
	}

	@Test
	public void writesLinesNoLongerThan75Octets() throws IOException {
		VComponent component = VComponent.createComponentFromBlob(read(icsFile));
		component.setEditable();
		for( String line : component.getCurrentBlob().split("\r?\n") ) {
			assertTrue("Line is longer than 75 octets: " + line, line.getBytes(StandardCharsets.UTF_8).length <= 75);
		}
	}

	/**
	 * The instances of the master component that start within the range given
	 * in the .range file must be those listed in the .instances file, which
	 * was worked out independently of aCal.
	 */
	@Test
	public void repeatsWhenExpected() throws IOException {
		final File expectedFile = sibling(".instances");
		assumeTrue("No .instances file", expectedFile.exists());

		check("instances", new Check() {
			public void run() throws IOException {
				List<String> expected = new ArrayList<String>();
				for( String line : read(expectedFile).split("\n") ) {
					if ( !line.isEmpty() && !line.startsWith("#") ) expected.add(line);
				}

				String[] range = read(sibling(".range")).trim().split(" ");
				VCalendar calendar = (VCalendar) VComponent.createComponentFromBlob(read(icsFile));
				AcalRepeatRule rule = AcalRepeatRule.fromVCalendar(calendar, 1, 1);
				assertNotNull("No repeat rule could be made", rule);

				List<String> found = new ArrayList<String>();
				for( AcalDateTime instance : rule.getInstancesInRange(
						rangeBound(range[0], range[2]), rangeBound(range[1], range[2])) ) {
					found.add(describe(instance));
				}

				assertEquals(lines(expected), lines(found));
			}
		});
	}

	private static AcalDateTime rangeBound(String when, String zone) {
		if ( zone.equals("UTC") ) return AcalDateTime.fromString(when);
		return AcalDateTime.fromIcalendar(when, null, zone.equals("floating") ? null : zone);
	}

	/**
	 * The start as the wall clock shows it in the event's time zone, followed
	 * by the same moment in UTC if the event has a time zone.
	 */
	private static String describe(AcalDateTime instance) {
		String local = instance.fmtIcal();
		if ( instance.isDate() || instance.isFloating() || local.endsWith("Z") ) return local;
		return local + " " + UTC_FORMAT.format(Instant.ofEpochMilli(instance.getMillis()));
	}

	private static String lines(List<String> lines) {
		StringBuilder joined = new StringBuilder();
		for( String line : lines ) joined.append(line).append('\n');
		return joined.toString();
	}

	/**
	 * Reduces an iCalendar text to a form in which two texts that say the same
	 * thing are equal: lines unfolded, the properties and the components
	 * inside each component sorted, and a new line always escaped as \\n (the
	 * RFC allows \\N as well, which is what aCal writes).
	 */
	static String canonical(String blob) {
		List<String> unfolded = new ArrayList<String>();
		for( String line : blob.split("\r?\n") ) {
			if ( (line.startsWith(" ") || line.startsWith("\t")) && !unfolded.isEmpty() ) {
				int last = unfolded.size() - 1;
				unfolded.set(last, unfolded.get(last) + line.substring(1));
			}
			else if ( !line.isEmpty() ) {
				unfolded.add(line);
			}
		}
		for( int i = 0; i < unfolded.size(); i++ ) unfolded.set(i, lowerCaseNewLineEscapes(unfolded.get(i)));
		int[] position = { 0 };
		StringBuilder canonical = new StringBuilder();
		while( position[0] < unfolded.size() ) canonical.append(canonicalComponent(unfolded, position, ""));
		return canonical.toString();
	}

	private static String lowerCaseNewLineEscapes(String line) {
		StringBuilder result = new StringBuilder(line.length());
		for( int i = 0; i < line.length(); i++ ) {
			char c = line.charAt(i);
			result.append(c);
			if ( c == '\\' && i + 1 < line.length() ) {
				char escaped = line.charAt(++i);
				result.append(escaped == 'N' ? 'n' : escaped);
			}
		}
		return result.toString();
	}

	private static String canonicalComponent(List<String> lines, int[] position, String indent) {
		String begin = lines.get(position[0]++);
		assertTrue("Expected BEGIN but found: " + begin, begin.startsWith("BEGIN:"));
		String name = begin.substring(6);

		List<String> properties = new ArrayList<String>();
		List<String> children = new ArrayList<String>();
		while( position[0] < lines.size() ) {
			String line = lines.get(position[0]);
			if ( line.startsWith("BEGIN:") ) {
				children.add(canonicalComponent(lines, position, indent + "  "));
			}
			else if ( line.equals("END:" + name) ) {
				position[0]++;
				break;
			}
			else {
				properties.add(line);
				position[0]++;
			}
		}
		Collections.sort(properties);
		Collections.sort(children);

		StringBuilder canonical = new StringBuilder(indent).append(name).append('\n');
		for( String property : properties ) canonical.append(indent).append("  ").append(property).append('\n');
		for( String child : children ) canonical.append(child);
		return canonical.toString();
	}
}
