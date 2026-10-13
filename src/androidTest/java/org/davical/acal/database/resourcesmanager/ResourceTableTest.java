package org.davical.acal.database.resourcesmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.RunningService;
import org.davical.acal.database.DMAction;
import org.davical.acal.database.DMDeleteQuery;
import org.davical.acal.database.DMInsertQuery;
import org.davical.acal.database.DMQueryList;
import org.davical.acal.database.DMUpdateQuery;
import org.davical.acal.database.DataChangeEvent;
import org.davical.acal.database.TableManager.QUERY_ACTION;
import org.davical.acal.database.TableManager;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;
import org.davical.acal.storage.LocalCalendars;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The table of resources as the jobs that synchronise use it: a list of
 * changes is applied together or not at all, and whoever listens is told of
 * what was changed, and only of that.
 */
@RunWith(AndroidJUnit4.class)
public class ResourceTableTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:%s\r\nDTSTART:20270315T100000Z\r\n"
			+ "SUMMARY:%s\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

	/** Something to do with the table on the resource manager's own thread. */
	private interface Work {
		void with( WriteableResourceTableManager table ) throws Exception;
	}

	private Context context;
	private RunningService service;
	private ResourceManager resources;
	private long calendar;
	private final List<DataChangeEvent> told = Collections.synchronizedList(new ArrayList<DataChangeEvent>());
	private final ResourceChangedListener listener = new ResourceChangedListener() {
		@Override
		public void resourceChanged( ResourceChangedEvent event ) {
			told.addAll(event.getChanges());
		}
	};

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		service = RunningService.start(context);
		resources = ResourceManager.getInstance(context);
		calendar = LocalCalendars.create(context.getContentResolver(), "Mine", "#00ff00", true, true, true);
		resources.addListener(listener);
	}

	@After
	public void tearDown() {
		if ( resources != null ) resources.removeListener(listener);
		if ( service != null ) service.stop();
	}

	private void onTheManagersThread( final Work work ) {
		resources.sendBlockingRequest(new BlockingResourceRequest() {
			private volatile boolean processed = false;

			@Override
			public void process( WriteableResourceTableManager table ) throws ResourceProcessingException {
				try {
					work.with(table);
				}
				catch( Exception e ) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public boolean isProcessed() {
				return processed;
			}

			@Override
			public void setProcessed() {
				processed = true;
			}
		});
	}

	private ContentValues resource( String name, String summary ) {
		ContentValues row = new ContentValues();
		row.put(ResourceTableManager.COLLECTION_ID, calendar);
		row.put(ResourceTableManager.RESOURCE_NAME, name);
		row.put(ResourceTableManager.ETAG, "\"" + summary + "\"");
		row.put(ResourceTableManager.RESOURCE_DATA, String.format(EVENT, name, summary));
		return row;
	}

	private List<String> toldOf( QUERY_ACTION action ) {
		List<String> names = new ArrayList<String>();
		synchronized( told ) {
			for( DataChangeEvent each : told ) {
				if ( each.action == action ) names.add(each.getData().getAsString(ResourceTableManager.RESOURCE_NAME));
			}
		}
		Collections.sort(names);
		return names;
	}

	private void addOneTwoAndThree() {
		final DMQueryList additions = new DMQueryList();
		additions.addAction(new DMInsertQuery(null, resource("one.ics", "One")));
		additions.addAction(new DMInsertQuery(null, resource("two.ics", "Two")));
		additions.addAction(new DMInsertQuery(null, resource("three.ics", "Three")));
		onTheManagersThread(new Work() {
			@Override
			public void with( WriteableResourceTableManager table ) {
				assertTrue(table.processActions(additions));
			}
		});
	}

	@Test
	public void aListOfChangesIsAppliedAndListenersAreToldOfEach() throws Exception {
		addOneTwoAndThree();
		assertEquals(3, TestDatabase.countNow("dav_resource", "collection_id = " + calendar));
		assertEquals("[one.ics, three.ics, two.ics]", toldOf(QUERY_ACTION.INSERT).toString());
		told.clear();

		final DMQueryList changes = new DMQueryList();
		ContentValues changed = new ContentValues();
		changed.put(ResourceTableManager.RESOURCE_DATA, String.format(EVENT, "one.ics", "Changed"));
		changes.addAction(new DMUpdateQuery(changed, ResourceTableManager.RESOURCE_NAME + " = ?", new String[] { "one.ics" }));
		changes.addAction(new DMDeleteQuery(ResourceTableManager.RESOURCE_NAME + " = ?", new String[] { "two.ics" }));
		changes.addAction(new DMInsertQuery(null, resource("four.ics", "Four")));
		onTheManagersThread(new Work() {
			@Override
			public void with( WriteableResourceTableManager table ) {
				assertTrue(table.processActions(changes));
			}
		});

		assertEquals(3, TestDatabase.countNow("dav_resource", "collection_id = " + calendar));
		assertEquals(1, TestDatabase.countNow("dav_resource", "name = 'one.ics' AND data LIKE '%SUMMARY:Changed%'"));
		assertEquals(0, TestDatabase.countNow("dav_resource", "name = 'two.ics'"));
		assertEquals("[four.ics]", toldOf(QUERY_ACTION.INSERT).toString());
		assertEquals("[two.ics]", toldOf(QUERY_ACTION.DELETE).toString());
		assertEquals(1, toldOf(QUERY_ACTION.UPDATE).size());
	}

	@Test
	public void whatASavedResourceHoldsIsWorkedOutFromItsData() throws Exception {
		addOneTwoAndThree();

		assertEquals("So that it can be found by kind and by when it happens", 3,
				TestDatabase.countNow("dav_resource", "effective_type = 'VEVENT' AND earliest_start IS NOT NULL"));
	}

	@Test
	public void aListThatFailsPartWayChangesNothing() throws Exception {
		addOneTwoAndThree();
		told.clear();

		final DMQueryList changes = new DMQueryList();
		changes.addAction(new DMDeleteQuery(ResourceTableManager.RESOURCE_NAME + " = ?", new String[] { "one.ics" }));
		changes.addAction(new DMInsertQuery(null, resource("four.ics", "Four")));
		changes.addAction(new DMAction() {
			@Override
			public void process( TableManager table ) {
				throw new IllegalStateException("The third change cannot be made");
			}
		});
		onTheManagersThread(new Work() {
			@Override
			public void with( WriteableResourceTableManager table ) {
				assertFalse(table.processActions(changes));
			}
		});

		assertEquals(3, TestDatabase.countNow("dav_resource", "collection_id = " + calendar));
		assertEquals(1, TestDatabase.countNow("dav_resource", "name = 'one.ics'"));
		assertEquals(0, TestDatabase.countNow("dav_resource", "name = 'four.ics'"));
		assertEquals("Nobody is told of a change that was undone", "[]", toldOf(QUERY_ACTION.INSERT).toString());
		assertEquals("Nobody is told of a change that was undone", "[]", toldOf(QUERY_ACTION.DELETE).toString());
	}
}
