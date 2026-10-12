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

import org.davical.acal.acaltime.AcalDateTime;
import org.junit.Test;

/**
 * A task or note made from nothing, as the editors make one, and then saved.
 */
public class NewComponentTest {

	private static String saved(Masterable component) {
		return ((VCalendar) component.getTopParent()).getCurrentBlob();
	}

	@Test
	public void aNewTaskIsSavedWithWhatWasTypedIntoIt() {
		VTodo todo = new VTodo();
		todo.setPercentComplete(0);
		assertEquals("", todo.getSummary());

		todo.setSummary("Buy milk");
		todo.setLocation("Corner shop");
		todo.setDescription("Two litres");
		todo.setPercentComplete(0);

		String blob = saved(todo);
		assertTrue(blob, blob.contains("SUMMARY:Buy milk"));
		assertTrue(blob, blob.contains("LOCATION:Corner shop"));
		assertTrue(blob, blob.contains("DESCRIPTION:Two litres"));

		VCalendar read = (VCalendar) VComponent.createComponentFromBlob(blob);
		assertEquals("Buy milk", read.getMasterChild().getSummary());
	}

	/** The task editor brings the timezones up to date just before it saves. */
	@Test
	public void aNewTaskKeepsWhatWasTypedWhenItsTimezonesAreUpdated() {
		VTodo todo = new VTodo();
		todo.setPercentComplete(0);
		todo.setSummary("Buy milk");
		todo.setDescription("Two litres");

		VCalendar calendar = (VCalendar) todo.getTopParent();
		calendar.updateTimeZones();

		String blob = calendar.getCurrentBlob();
		assertTrue(blob, blob.contains("SUMMARY:Buy milk"));
		assertTrue(blob, blob.contains("DESCRIPTION:Two litres"));
	}

	@Test
	public void aNewNoteIsSavedWithWhatWasTypedIntoIt() {
		VJournal journal = new VJournal();
		journal.setStart(new AcalDateTime());

		journal.setSummary("Shopping list");
		journal.setDescription("Milk, eggs");

		String blob = saved(journal);
		assertTrue(blob, blob.contains("SUMMARY:Shopping list"));
		assertTrue(blob, blob.contains("DESCRIPTION:Milk\\, eggs"));

		VCalendar read = (VCalendar) VComponent.createComponentFromBlob(blob);
		assertEquals("Shopping list", read.getMasterChild().getSummary());
	}
}
