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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes a calendar as one iCalendar file, for taking a copy of a
 * collection and for bringing one in.
 *
 * In the database each event, task or note is a calendar of its own, holding
 * the component, any instances of it that were changed, and the timezones it
 * mentions.  A file is one calendar holding all of them.  Going either way
 * the components are carried over as text, exactly as they were, so nothing
 * we do not understand is lost.
 */
public final class IcsFile {

	private static final String EOL = "\r\n";
	private static final String PRODID = "-//morphoss.com//aCal 1.0//EN";

	private static final Pattern TZID_PARAM = Pattern.compile(";TZID=(\"?)([^\":;]+)\\1", Pattern.CASE_INSENSITIVE);

	private IcsFile() {
	}

	/** One event, task or note as it comes out of a file: what a resource is made from. */
	public static final class Item {
		/** The UID that says which event, task or note this is */
		public final String uid;
		/** VEVENT, VTODO or VJOURNAL */
		public final String type;
		/** A whole calendar holding just this, ready to be stored as a resource */
		public final String data;

		Item(String uid, String type, String data) {
			this.uid = uid;
			this.type = type;
			this.data = data;
		}
	}

	/** A component directly inside a VCALENDAR, as the lines it was written in. */
	private static final class Block {
		final String name;
		final List<String> lines = new ArrayList<String>();

		Block(String name) {
			this.name = name;
		}

		/**
		 * @return the value of a property of this component itself, and not of
		 *         one inside it such as an alarm, or null if it has none
		 */
		String property(String wanted) {
			int depth = 0;
			for ( int i = 0; i < lines.size(); i++ ) {
				String line = lines.get(i);
				if ( startsWithIgnoreCase(line, "BEGIN:") ) depth++;
				else if ( startsWithIgnoreCase(line, "END:") ) depth--;
				else if ( depth == 1 && startsWithIgnoreCase(line, wanted)
							&& line.length() > wanted.length()
							&& (line.charAt(wanted.length()) == ':' || line.charAt(wanted.length()) == ';') ) {
					// A long value is carried on over lines that begin with a space
					StringBuilder whole = new StringBuilder(line);
					while ( i + 1 < lines.size() && isContinuation(lines.get(i + 1)) ) {
						whole.append(lines.get(++i).substring(1));
					}
					return whole.substring(whole.indexOf(":") + 1).trim();
				}
			}
			return null;
		}

		Set<String> timezonesMentioned() {
			Set<String> tzids = new LinkedHashSet<String>();
			for ( String line : lines ) {
				Matcher m = TZID_PARAM.matcher(line);
				while ( m.find() ) tzids.add(m.group(2));
			}
			return tzids;
		}

		void appendTo(StringBuilder out) {
			for ( String line : lines ) out.append(line).append(EOL);
		}
	}

	private static boolean startsWithIgnoreCase(String line, String prefix) {
		return line.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	private static boolean isContinuation(String line) {
		return line.length() > 0 && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
	}

	private static boolean isCalendarItem(String name) {
		return name.equals("VEVENT") || name.equals("VTODO") || name.equals("VJOURNAL");
	}

	/**
	 * @return the components directly inside each VCALENDAR in the text, in
	 *         the order they come.  Anything outside a VCALENDAR, such as a
	 *         contact, is left out.
	 */
	private static List<Block> blocksOf(String text) {
		List<Block> blocks = new ArrayList<Block>();
		if ( text == null ) return blocks;

		int depth = 0;			// 1 is inside a VCALENDAR, 2 inside a component of it
		Block current = null;
		for ( String line : text.split("\r?\n|\r") ) {
			if ( depth == 0 ) {
				if ( line.trim().equalsIgnoreCase("BEGIN:VCALENDAR") ) depth = 1;
				continue;
			}
			if ( startsWithIgnoreCase(line, "BEGIN:") ) {
				if ( depth == 1 ) current = new Block(line.substring(6).trim().toUpperCase());
				depth++;
			}
			if ( current != null ) current.lines.add(line);
			if ( startsWithIgnoreCase(line, "END:") ) {
				depth--;
				if ( depth == 1 && current != null ) {
					blocks.add(current);
					current = null;
				}
			}
		}
		return blocks;
	}

	private static void begin(StringBuilder out) {
		out.append("BEGIN:VCALENDAR").append(EOL);
		out.append("VERSION:2.0").append(EOL);
		out.append("PRODID:").append(PRODID).append(EOL);
	}

	private static void end(StringBuilder out) {
		out.append("END:VCALENDAR").append(EOL);
	}

	/**
	 * @param resources the data of each resource of a collection
	 * @return one calendar holding every event, task and note in them, with
	 *         each timezone they mention once
	 */
	public static String export(List<String> resources) {
		Map<String, Block> timezones = new LinkedHashMap<String, Block>();
		List<Block> items = new ArrayList<Block>();

		for ( String resource : resources ) {
			for ( Block block : blocksOf(resource) ) {
				if ( block.name.equals("VTIMEZONE") ) {
					String tzid = block.property("TZID");
					if ( tzid != null && !timezones.containsKey(tzid) ) timezones.put(tzid, block);
				}
				else if ( isCalendarItem(block.name) ) {
					items.add(block);
				}
			}
		}

		StringBuilder out = new StringBuilder();
		begin(out);
		for ( Block timezone : timezones.values() ) timezone.appendTo(out);
		for ( Block item : items ) item.appendTo(out);
		end(out);
		return out.toString();
	}

	/**
	 * @return how many events, tasks and notes a set of resources holds, which
	 *         is how many export() would write.  A changed instance of a
	 *         repeating event is part of that event.
	 */
	public static int count(List<String> resources) {
		Set<String> uids = new LinkedHashSet<String>();
		int withNoUid = 0;
		for ( String resource : resources ) {
			for ( Block block : blocksOf(resource) ) {
				if ( !isCalendarItem(block.name) ) continue;
				String uid = block.property("UID");
				if ( uid == null ) withNoUid++;
				else uids.add(uid);
			}
		}
		return uids.size() + withNoUid;
	}

	/**
	 * @param file the text of an iCalendar file
	 * @return each event, task and note in it as a calendar of its own, with
	 *         the instances of it that were changed and the timezones it
	 *         mentions.  They are in the order they first appear.
	 */
	public static List<Item> split(String file) {
		Map<String, Block> timezones = new LinkedHashMap<String, Block>();
		Map<String, List<Block>> byUid = new LinkedHashMap<String, List<Block>>();

		for ( Block block : blocksOf(file) ) {
			if ( block.name.equals("VTIMEZONE") ) {
				String tzid = block.property("TZID");
				if ( tzid != null && !timezones.containsKey(tzid) ) timezones.put(tzid, block);
			}
			else if ( isCalendarItem(block.name) ) {
				String uid = block.property("UID");
				if ( uid == null || uid.equals("") ) {
					// It has to have one to be told from the rest, so it is given one
					uid = UUID.randomUUID().toString();
					block.lines.add(1, "UID:" + uid);
				}
				// The same UID for a task and an event is two things, not one
				String key = block.name + "\n" + uid;
				if ( !byUid.containsKey(key) ) byUid.put(key, new ArrayList<Block>());
				byUid.get(key).add(block);
			}
		}

		List<Item> items = new ArrayList<Item>();
		for ( List<Block> blocks : byUid.values() ) {
			Set<String> mentioned = new LinkedHashSet<String>();
			for ( Block block : blocks ) mentioned.addAll(block.timezonesMentioned());

			StringBuilder out = new StringBuilder();
			begin(out);
			for ( String tzid : mentioned ) {
				Block timezone = timezones.get(tzid);
				if ( timezone != null ) timezone.appendTo(out);
			}
			for ( Block block : blocks ) block.appendTo(out);
			end(out);

			items.add(new Item(blocks.get(0).property("UID"), blocks.get(0).name, out.toString()));
		}
		return items;
	}

	/**
	 * @return the UID of the event, task or note a resource holds, or null if
	 *         it holds none or it has no UID
	 */
	public static String uidOf(String resource) {
		for ( Block block : blocksOf(resource) ) {
			if ( isCalendarItem(block.name) ) return block.property("UID");
		}
		return null;
	}
}
