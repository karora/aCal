package org.davical.acal.database.cachemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.RunningService;
import org.davical.acal.Saving;
import org.davical.acal.Wait;
import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.cachemanager.requests.CRObjectsInRange;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.database.resourcesmanager.requests.RRRequestInstance;
import org.davical.acal.dataservice.CalendarInstance;
import org.davical.acal.storage.LocalCalendars;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The cache is only a copy of what the resources say, and a copy that is
 * out of date has to be noticed and put right: nothing else ever corrects
 * it, so an event shown from it can be one that is not there.
 */
@RunWith(AndroidJUnit4.class)
public class StaleCacheTest {

	private Context context;
	private RunningService service;
	private long calendar;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		service = RunningService.start(context);
		calendar = LocalCalendars.create(context.getContentResolver(), "Mine", "#00ff00", true, true, true);
	}

	@After
	public void tearDown() {
		if ( service != null ) service.stop();
	}

	private static String event( String summary, String day ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:far@example.net\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "DTSTART:" + day + "T100000Z\r\nDTEND:" + day + "T110000Z\r\n"
				+ "SUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	/** What a screen is given for these days of June 2029. */
	private List<String> shown( int firstDay, int lastDay ) throws Exception {
		AcalDateRange range = new AcalDateRange(new AcalDateTime(2029, 6, firstDay, 0, 0, 0, "UTC"),
				new AcalDateTime(2029, 6, lastDay, 23, 59, 59, "UTC"));
		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicReference<ArrayList<CacheObject>> answer = new AtomicReference<ArrayList<CacheObject>>();
		CacheManager.getInstance(context).sendRequest(new CRObjectsInRange(range,
				new CacheResponseListener<ArrayList<CacheObject>>() {
					@Override
					public void cacheResponse( CacheResponse<ArrayList<CacheObject>> response ) {
						answer.set(response.result());
						answered.countDown();
					}
				}));
		assertTrue("The cache did not answer", answered.await(30, TimeUnit.SECONDS));
		List<String> summaries = new ArrayList<String>();
		for( CacheObject each : answer.get() ) {
			summaries.add(each.getSummary());
		}
		Collections.sort(summaries);
		return summaries;
	}

	private void waitUntilShown( final int firstDay, final int lastDay, final String... summaries ) throws Exception {
		Wait.until("June " + firstDay + " to " + lastDay + " shows " + Arrays.toString(summaries),
				new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return shown(firstDay, lastDay).equals(Arrays.asList(summaries));
					}
				});
	}

	/**
	 * The cache was marked as up to date as soon as the resources had been
	 * written, before it had done anything about them, and with no cache
	 * running there was nothing to do anything about them at all.  A cache
	 * started after that went on showing the event as it was.
	 */
	@Test
	public void aChangeTheCacheNeverHeardOfIsShownOnceItStartsAgain() throws Exception {
		long id = Saving.send(context, calendar, -1, event("Far away", "20290615"), RRResourceEditedRequest.ACTION_CREATE);
		waitUntilShown(14, 16, "Far away");

		CacheManager.getInstance(context).close();
		Saving.send(context, calendar, id, event("Moved", "20290616"), RRResourceEditedRequest.ACTION_UPDATE);
		Wait.untilThereAreNone("pending_change", null);

		waitUntilShown(14, 16, "Moved");
		assertEquals("It is no longer on the day it was", 0, shown(15, 15).size());
	}

	/**
	 * However a row of the cache came to point at an event that has gone,
	 * opening it said the event had been changed or deleted, and the row
	 * stayed: the event went on being shown, and no sync took it away.
	 */
	@Test
	public void anEventThatIsNotThereIsTakenOutOfTheCacheWhenItIsOpened() throws Exception {
		long id = Saving.send(context, calendar, -1, event("Far away", "20290615"), RRResourceEditedRequest.ACTION_CREATE);
		waitUntilShown(14, 16, "Far away");

		SQLiteDatabase db = TestDatabase.open();
		try {
			db.execSQL("DELETE FROM pending_change WHERE resource_id = " + id);
			db.execSQL("DELETE FROM dav_resource WHERE _id = " + id);
		}
		finally {
			db.close();
		}
		assertEquals(1, TestDatabase.countNow("event_cache", "resource_id = " + id));

		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicBoolean found = new AtomicBoolean();
		ResourceManager.getInstance(context).sendRequest(new RRRequestInstance(
				new ResourceResponseListener<CalendarInstance>() {
					@Override
					public void resourceResponse( ResourceResponse<CalendarInstance> response ) {
						found.set(response.wasSuccessful());
						answered.countDown();
					}
				}, id, null));
		assertTrue("Opening the event was not answered", answered.await(30, TimeUnit.SECONDS));
		assertFalse(found.get());

		Wait.untilThereAreNone("event_cache", "resource_id = " + id);
		assertEquals(0, shown(14, 16).size());
	}
}
