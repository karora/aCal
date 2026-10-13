package org.davical.acal.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.RunningService;
import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.cachemanager.CacheManager;
import org.davical.acal.database.cachemanager.CacheObject;
import org.davical.acal.database.cachemanager.CacheResponse;
import org.davical.acal.database.cachemanager.CacheResponseListener;
import org.davical.acal.database.cachemanager.requests.CRObjectsInRange;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.davacal.VComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Saving to a calendar that is kept on the phone, from the request a screen
 * sends to the rows it ends up as.  There is no server: the change is queued
 * like any other, and the job that drains the queue writes it straight to the
 * resource, with nothing left waiting.
 */
@RunWith(AndroidJUnit4.class)
public class LocalCalendarWritesTest {

	private static final long DAY = 24 * 60 * 60 * 1000L;

	private Context context;
	private ContentResolver cr;
	private RunningService service;
	private long calendar;
	private String day;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		cr = context.getContentResolver();
		service = RunningService.start(context);
		calendar = LocalCalendars.create(cr, "Mine", "#00ff00", true, true, true);

		// The day after tomorrow, so that an alarm for it is still to come
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		day = format.format(new Date(System.currentTimeMillis() + 2 * DAY));
	}

	@After
	public void tearDown() {
		if ( service != null ) service.stop();
	}

	private String event( String uid, String summary ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "DTSTART:" + day + "T100000Z\r\nDTEND:" + day + "T110000Z\r\n"
				+ "SUMMARY:" + summary + "\r\n"
				+ "BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:" + summary + "\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\n"
				+ "END:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	private static String task( String uid, String summary ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VTODO\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "SUMMARY:" + summary + "\r\nEND:VTODO\r\nEND:VCALENDAR\r\n";
	}

	private static String note( String uid, String summary ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VJOURNAL\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "SUMMARY:" + summary + "\r\nEND:VJOURNAL\r\nEND:VCALENDAR\r\n";
	}

	/** Send what a screen sends when the user saves, and wait for the answer it gets. */
	private long send( long collectionId, long resourceId, String data, int action ) throws Exception {
		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicReference<Long> answer = new AtomicReference<Long>();
		ResourceManager.getInstance(context).sendRequest(new RRResourceEditedRequest(
				new ResourceResponseListener<Long>() {
					@Override
					public void resourceResponse( ResourceResponse<Long> response ) {
						answer.set(response.result());
						answered.countDown();
					}
				}, collectionId, resourceId, VComponent.createComponentFromBlob(data), action));
		assertTrue("The save was not answered", answered.await(30, TimeUnit.SECONDS));
		assertNotNull("The save was refused", answer.get());
		return answer.get();
	}

	private long create( String data ) throws Exception {
		long id = send(calendar, -1, data, RRResourceEditedRequest.ACTION_CREATE);
		waitUntilNothingIsWaiting();
		return id;
	}

	private static long count( String table, String where ) throws Exception {
		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			return TestDatabase.count(db, table, where);
		}
		finally {
			db.close();
		}
	}

	private static String string( String sql ) throws Exception {
		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			Cursor c = db.rawQuery(sql, (String[]) null);
			try {
				return c.moveToFirst() ? c.getString(0) : null;
			}
			finally {
				c.close();
			}
		}
		finally {
			db.close();
		}
	}

	private static void waitUntil( String what, Callable<Boolean> condition ) throws Exception {
		long giveUp = System.currentTimeMillis() + 30000;
		Exception last = null;
		while( System.currentTimeMillis() < giveUp ) {
			try {
				if ( condition.call() ) return;
				last = null;
			}
			catch( android.database.sqlite.SQLiteException busy ) {
				// Something is being written: look again
				last = busy;
			}
			Thread.sleep(100);
		}
		fail("Waited 30 seconds and still not true: " + what + (last == null ? "" : " (" + last + ")"));
	}

	private static void waitUntilThereAreNone( final String table, final String where ) throws Exception {
		waitUntil("no rows in " + table + " where " + where, new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count(table, where) == 0;
			}
		});
	}

	private static void waitUntilNothingIsWaiting() throws Exception {
		waitUntil("no change is waiting to be written", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count("pending_change", null) == 0;
			}
		});
	}

	/** What a calendar screen is given to show for the day of the event. */
	private ArrayList<CacheObject> shownOnTheDay() throws InterruptedException {
		AcalDateTime start = AcalDateTime.fromMillis(System.currentTimeMillis() + DAY);
		AcalDateTime end = AcalDateTime.fromMillis(System.currentTimeMillis() + 4 * DAY);
		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicReference<ArrayList<CacheObject>> shown = new AtomicReference<ArrayList<CacheObject>>();
		CacheManager.getInstance(context).sendRequest(new CRObjectsInRange(new AcalDateRange(start, end),
				new CacheResponseListener<ArrayList<CacheObject>>() {
					@Override
					public void cacheResponse( CacheResponse<ArrayList<CacheObject>> response ) {
						shown.set(response.result());
						answered.countDown();
					}
				}));
		assertTrue("The cache did not answer", answered.await(30, TimeUnit.SECONDS));
		return shown.get();
	}

	private void waitUntilTheDayShows( final String... summaries ) throws Exception {
		waitUntil("the day shows " + java.util.Arrays.toString(summaries), new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				ArrayList<String> shown = new ArrayList<String>();
				for( CacheObject each : shownOnTheDay() ) {
					shown.add(each.getSummary());
				}
				java.util.Collections.sort(shown);
				return shown.equals(java.util.Arrays.asList(summaries));
			}
		});
	}

	@Test
	public void aNewEventIsWrittenStraightToTheCalendar() throws Exception {
		long id = create(event("one@example.net", "One"));

		assertEquals(1, count("dav_resource", "_id = " + id + " AND collection_id = " + calendar
				+ " AND effective_type = 'VEVENT' AND name = 'one@example.net.ics'"));
		assertTrue(string("SELECT data FROM dav_resource WHERE _id = " + id).contains("SUMMARY:One"));
		assertTrue("It has an etag, as a resource that has been saved does",
				string("SELECT etag FROM dav_resource WHERE _id = " + id).startsWith("\"local-"));
		assertEquals("Nothing is left waiting to be sent anywhere", 0, count("pending_change", null));
	}

	@Test
	public void aTaskAndANoteAreWrittenAsWhatTheyAre() throws Exception {
		long task = create(task("task@example.net", "Do it"));
		long note = create(note("note@example.net", "Remember"));

		assertEquals(1, count("dav_resource", "_id = " + task + " AND effective_type = 'VTODO'"));
		assertEquals(1, count("dav_resource", "_id = " + note + " AND effective_type = 'VJOURNAL'"));
		assertEquals(0, count("pending_change", null));
	}

	@Test
	public void anEditReplacesWhatWasThere() throws Exception {
		long id = create(event("one@example.net", "One"));
		String firstEtag = string("SELECT etag FROM dav_resource WHERE _id = " + id);

		Thread.sleep(5);
		assertEquals(id, send(calendar, id, event("one@example.net", "Changed"), RRResourceEditedRequest.ACTION_UPDATE));
		waitUntilNothingIsWaiting();

		assertEquals(1, count("dav_resource", "collection_id = " + calendar));
		String data = string("SELECT data FROM dav_resource WHERE _id = " + id);
		assertTrue(data, data.contains("SUMMARY:Changed"));
		assertNotEquals("A resource that has changed has a new etag", firstEtag,
				string("SELECT etag FROM dav_resource WHERE _id = " + id));
		assertEquals("one@example.net.ics", string("SELECT name FROM dav_resource WHERE _id = " + id));
	}

	@Test
	public void aDeleteRemovesIt() throws Exception {
		long keep = create(event("keep@example.net", "Keep"));
		long id = create(event("one@example.net", "One"));

		send(calendar, id, event("one@example.net", "One"), RRResourceEditedRequest.ACTION_DELETE);
		waitUntilNothingIsWaiting();

		assertEquals(0, count("dav_resource", "_id = " + id));
		assertEquals(1, count("dav_resource", "_id = " + keep));
	}

	@Test
	public void theCalendarScreensAreGivenEachChange() throws Exception {
		long id = create(event("one@example.net", "One"));
		waitUntilTheDayShows("One");

		create(event("two@example.net", "Two"));
		waitUntilTheDayShows("One", "Two");

		send(calendar, id, event("one@example.net", "Changed"), RRResourceEditedRequest.ACTION_UPDATE);
		waitUntilTheDayShows("Changed", "Two");

		send(calendar, id, event("one@example.net", "Changed"), RRResourceEditedRequest.ACTION_DELETE);
		waitUntilTheDayShows("Two");
	}

	@Test
	public void anAlarmIsQueuedForAnEventAndGoesWhenTheEventDoes() throws Exception {
		final long id = create(event("one@example.net", "One"));
		waitUntil("the event has an alarm queued", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count("alarms", "rid = " + id) == 1;
			}
		});

		send(calendar, id, event("one@example.net", "One"), RRResourceEditedRequest.ACTION_DELETE);
		waitUntil("the alarm has gone with its event", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count("alarms", "rid = " + id) == 0;
			}
		});
	}

	@Test
	public void deletingTheCalendarTakesEverythingInItAndNothingElse() throws Exception {
		long other = LocalCalendars.create(cr, "Other", "#0000ff", true, true, true);
		final long id = create(event("one@example.net", "One"));
		create(task("task@example.net", "Do it"));
		long kept = send(other, -1, event("kept@example.net", "Kept"), RRResourceEditedRequest.ACTION_CREATE);
		waitUntilNothingIsWaiting();
		waitUntilTheDayShows("Kept", "One");
		waitUntil("the event has an alarm queued", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count("alarms", "rid = " + id) == 1;
			}
		});

		LocalCalendars.delete(context, calendar);

		waitUntilThereAreNone("dav_resource", "collection_id = " + calendar);
		waitUntilThereAreNone("pending_change", "collection_id = " + calendar);
		waitUntilThereAreNone("event_cache", "collection_id = " + calendar);
		waitUntilThereAreNone("alarms", "rid = " + id);
		assertNull(org.davical.acal.providers.DavCollections.getRow(calendar, cr));
		assertEquals(1, count("dav_resource", "_id = " + kept));
		waitUntilTheDayShows("Kept");
		assertEquals("The other calendar keeps its alarm", 1, count("alarms", "rid = " + kept));
	}

	@Test
	public void aChangeWaitingForACalendarThatIsDeletedGoesWithIt() throws Exception {
		// Straight into the queue, as if the job that writes changes had not got to it
		SQLiteDatabase db = TestDatabase.open();
		db.execSQL("INSERT INTO dav_resource (collection_id, data) VALUES (" + calendar + ", 'BEGIN:VCALENDAR')");
		db.execSQL("INSERT INTO pending_change (collection_id, resource_id, new_data, uid)"
				+ " SELECT collection_id, _id, data, 'waiting' FROM dav_resource WHERE collection_id = " + calendar);
		db.execSQL("UPDATE pending_change SET state = 1");
		db.close();
		assertEquals(1, count("pending_change", "collection_id = " + calendar));

		LocalCalendars.delete(context, calendar);

		waitUntil("the change has gone with its calendar", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return count("pending_change", null) == 0 && count("dav_resource", null) == 0;
			}
		});
	}
}
