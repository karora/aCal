package org.davical.acal.database.cachemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.RunningService;
import org.davical.acal.Saving;
import org.davical.acal.Wait;
import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.cachemanager.requests.CRClearCacheRequest;
import org.davical.acal.database.cachemanager.requests.CRObjectsInRange;
import org.davical.acal.storage.LocalCalendars;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The cache holds what the calendar screens show, one row to each time an
 * event happens, for a range of days around the ones that have been looked
 * at.  It is filled from the resources when a screen asks for days outside
 * that range, and can be thrown away and made again at any time.
 */
@RunWith(AndroidJUnit4.class)
public class CacheWindowTest {

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

	private static String event( String uid, String summary, String day, String extra ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "DTSTART:" + day + "T100000Z\r\nDTEND:" + day + "T110000Z\r\n" + extra
				+ "SUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	private long save( String data ) throws Exception {
		long id = Saving.send(context, calendar, -1, data, RRResourceEditedRequest.ACTION_CREATE);
		Wait.untilThereAreNone("pending_change", null);
		return id;
	}

	/** What a screen is given for these days, which is nothing until the cache covers them. */
	private List<String> shown( int year, int month, int firstDay, int lastDay ) throws Exception {
		AcalDateRange range = new AcalDateRange(new AcalDateTime(year, month, firstDay, 0, 0, 0, "UTC"),
				new AcalDateTime(year, month, lastDay, 23, 59, 59, "UTC"));
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

	private void waitUntilShown( final int year, final int month, final int firstDay, final int lastDay,
			final String... summaries ) throws Exception {
		Wait.until(year + "-" + month + "-" + firstDay + " to " + lastDay + " shows " + Arrays.toString(summaries),
				new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return shown(year, month, firstDay, lastDay).equals(Arrays.asList(summaries));
					}
				});
	}

	@Test
	public void daysFarFromTodayAreFilledInWhenTheyAreAskedFor() throws Exception {
		long id = save(event("far@example.net", "Far away", "20290615", ""));

		waitUntilShown(2029, 6, 14, 16, "Far away");

		assertEquals(1, TestDatabase.countNow("event_cache", "resource_id = " + id));
	}

	@Test
	public void askingAgainForTheSameDaysDoesNotShowThingsTwice() throws Exception {
		save(event("far@example.net", "Far away", "20290615", ""));
		waitUntilShown(2029, 6, 14, 16, "Far away");

		waitUntilShown(2029, 6, 1, 30, "Far away");
		waitUntilShown(2029, 6, 14, 16, "Far away");

		assertEquals(1, TestDatabase.countNow("event_cache", "summary = 'Far away'"));
	}

	@Test
	public void anEventThatRepeatsIsShownEachTimeItHappens() throws Exception {
		long id = save(event("daily@example.net", "Daily", "20290610", "RRULE:FREQ=DAILY;COUNT=5\r\n"));

		waitUntilShown(2029, 6, 9, 20, "Daily", "Daily", "Daily", "Daily", "Daily");
		assertEquals(2, shown(2029, 6, 11, 12).size());

		assertEquals(5, TestDatabase.countNow("event_cache", "resource_id = " + id));
	}

	@Test
	public void aChangeToAnEventInDaysAlreadyFilledInIsShown() throws Exception {
		long id = save(event("far@example.net", "Far away", "20290615", ""));
		waitUntilShown(2029, 6, 14, 16, "Far away");

		Saving.send(context, calendar, id, event("far@example.net", "Moved", "20290616", ""),
				RRResourceEditedRequest.ACTION_UPDATE);

		waitUntilShown(2029, 6, 16, 16, "Moved");
		assertEquals("It is no longer on the day it was", 0, shown(2029, 6, 15, 15).size());
	}

	@Test
	public void theCacheCanBeThrownAwayAndIsMadeAgainFromTheResources() throws Exception {
		save(event("far@example.net", "Far away", "20290615", ""));
		save(event("daily@example.net", "Daily", "20290614", "RRULE:FREQ=DAILY;COUNT=2\r\n"));
		waitUntilShown(2029, 6, 14, 16, "Daily", "Daily", "Far away");

		CacheManager.getInstance(context).sendRequest(new CRClearCacheRequest());

		waitUntilShown(2029, 6, 14, 16, "Daily", "Daily", "Far away");
		assertEquals(3, TestDatabase.countNow("event_cache", "summary IN ('Daily', 'Far away')"));
	}
}
