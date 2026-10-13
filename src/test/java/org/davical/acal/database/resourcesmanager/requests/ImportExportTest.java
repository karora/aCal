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

package org.davical.acal.database.resourcesmanager.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.storage.IcsFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentValues;

/**
 * Taking a copy of a collection as a file and bringing a file in, against a
 * stand-in for the resource table.
 */
@RunWith(RobolectricTestRunner.class)
public class ImportExportTest {

	private static final long COLLECTION = 37;
	private static final long OTHER_COLLECTION = 38;

	private static String event(String uid, String summary) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//morphoss.com//aCal 1.0//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:" + uid + "\r\nSUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	/** The rows of dav_resource and pending_change, and a note of each change asked for. */
	private final List<ContentValues> resources = new ArrayList<ContentValues>();
	private final List<ContentValues> pendings = new ArrayList<ContentValues>();
	private final List<String> added = new ArrayList<String>();
	private long nextId = 100;
	private WriteableResourceTableManager table;

	private void resource(long id, long collectionId, String data) {
		ContentValues row = new ContentValues();
		row.put(ResourceTableManager.RESOURCE_ID, id);
		row.put(ResourceTableManager.COLLECTION_ID, collectionId);
		row.put(ResourceTableManager.RESOURCE_DATA, data);
		resources.add(row);
	}

	private void pending(long resourceId, long collectionId, String newData) {
		ContentValues row = new ContentValues();
		row.put(ResourceTableManager.PEND_RESOURCE_ID, resourceId);
		row.put(ResourceTableManager.PEND_COLLECTION_ID, collectionId);
		row.put(ResourceTableManager.NEW_DATA, newData);
		pendings.add(row);
	}

	@Before
	public void setUp() {
		table = (WriteableResourceTableManager) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { WriteableResourceTableManager.class }, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ( method.getName().equals("query") ) {
					// Only ever asked for the resources of one collection
					ArrayList<ContentValues> rows = new ArrayList<ContentValues>();
					for ( ContentValues row : resources ) {
						if ( row.getAsString(ResourceTableManager.COLLECTION_ID).equals(((String[]) args[2])[0]) ) rows.add(row);
					}
					return rows;
				}
				if ( method.getName().equals("getPendingResources") ) {
					return new ArrayList<ContentValues>(pendings);
				}
				if ( method.getName().equals("addPending") ) {
					long resourceId = (Long) args[1];
					if ( args[2] == null ) resourceId = nextId++;
					added.add(args[0] + "|" + (args[2] == null ? "new" : resourceId) + "|" + args[4]
							+ "|" + (args[2] == null ? "" : IcsFile.split((String) args[2]).get(0).data.equals(args[2])));
					return resourceId;
				}
				throw new UnsupportedOperationException(method.getName());
			}
		});
	}

	private RRImportItems.Counts importing(String... files) throws Exception {
		StringBuilder file = new StringBuilder();
		for ( String f : files ) file.append(f);
		RRImportItems request = new RRImportItems(COLLECTION, IcsFile.split(file.toString()));
		request.process(table);
		return request.getResponse().result();
	}

	private List<String> exported() throws Exception {
		RRCollectionData request = new RRCollectionData(COLLECTION);
		request.process(table);
		List<String> data = request.getResponse().result();
		Collections.sort(data);
		return data;
	}

	@Test
	public void whatIsNotThereYetIsAdded() throws Exception {
		RRImportItems.Counts counts = importing(event("one", "First"), event("two", "Second"));

		assertEquals(2, counts.added);
		assertEquals(0, counts.replaced);
		assertEquals(0, counts.unchanged);
		assertEquals(Arrays.asList("37|new|one|", "37|new|two|"), added);
	}

	/** Or importing the same file twice would make two of everything. */
	@Test
	public void whatIsThereAlreadyIsReplacedAndNotAddedAgain() throws Exception {
		resource(1, COLLECTION, event("one", "First"));

		RRImportItems.Counts counts = importing(event("one", "First, changed"), event("two", "Second"));

		assertEquals(1, counts.added);
		assertEquals(1, counts.replaced);
		assertEquals(Arrays.asList("37|1|one|true", "37|new|two|"), added);
	}

	@Test
	public void whatIsThereAlreadyAndTheSameIsLeftAlone() throws Exception {
		resource(1, COLLECTION, event("one", "First"));

		RRImportItems.Counts counts = importing(event("one", "First"));

		assertEquals(0, counts.added);
		assertEquals(0, counts.replaced);
		assertEquals(1, counts.unchanged);
		assertTrue(added.isEmpty());
	}

	@Test
	public void theSameThingInAnotherCollectionIsNoneOfOurBusiness() throws Exception {
		resource(1, OTHER_COLLECTION, event("one", "First"));

		RRImportItems.Counts counts = importing(event("one", "First"));

		assertEquals(1, counts.added);
	}

	@Test
	public void aFileHoldingTheSameThingTwiceAddsItOnce() throws Exception {
		// Two calendars in the one file, each with its own copy
		String file = event("one", "First") + event("one", "First");
		RRImportItems request = new RRImportItems(COLLECTION,
				Arrays.asList(IcsFile.split(event("one", "First")).get(0), IcsFile.split(event("one", "First")).get(0)));
		request.process(table);

		assertEquals(1, request.getResponse().result().added);
		assertEquals(1, request.getResponse().result().unchanged);
		assertEquals(1, IcsFile.split(file).size());
	}

	/** What is compared with is what the user sees, which may be a change not committed yet. */
	@Test
	public void aChangeStillWaitingCountsAsWhatIsThere() throws Exception {
		resource(1, COLLECTION, event("one", "First"));
		pending(1, COLLECTION, event("one", "First, changed here"));

		assertEquals(1, importing(event("one", "First, changed here")).unchanged);
		assertEquals(1, importing(event("one", "First")).replaced);
	}

	@Test
	public void exportHasTheChangesStillWaitingAndNotWhatIsToBeDeleted() throws Exception {
		resource(1, COLLECTION, event("one", "First"));
		resource(2, COLLECTION, event("two", "Second"));
		resource(3, COLLECTION, event("three", "Third"));
		resource(4, OTHER_COLLECTION, event("four", "Fourth"));
		pending(2, COLLECTION, event("two", "Second, changed"));
		pending(3, COLLECTION, null);
		pending(4, OTHER_COLLECTION, event("four", "Fourth, changed"));

		List<String> expected = new ArrayList<String>(Arrays.asList(event("one", "First"), event("two", "Second, changed")));
		Collections.sort(expected);
		assertEquals(expected, exported());
	}

	@Test
	public void anEmptyCollectionExportsNothing() throws Exception {
		assertTrue(exported().isEmpty());
		assertNull(IcsFile.uidOf(IcsFile.export(exported())));
	}
}
