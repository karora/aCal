package org.davical.acal.database.alarmmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.content.Context;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.RunningService;
import org.davical.acal.Saving;
import org.davical.acal.Wait;
import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import org.davical.acal.database.alarmmanager.requests.ARRebuildRequest;
import org.davical.acal.database.alarmmanager.requests.ARUpdateAlarmState;
import org.davical.acal.database.alarmmanager.requesttypes.BlockingAlarmRequest;
import org.davical.acal.storage.LocalCalendars;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The queue of alarms still to go off.  What the user has done with an alarm
 * (dismissed it, snoozed it) has to outlast the event being edited and the
 * queue being made again, or the alarm comes back.
 */
@RunWith(AndroidJUnit4.class)
public class AlarmQueueTest {

	private static final long MINUTE = 60 * 1000L;
	private static final long DAY = 24 * 60 * MINUTE;

	private Context context;
	private RunningService service;
	private AlarmQueueManager alarms;
	private long calendar;
	private String day;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		service = RunningService.start(context);
		alarms = AlarmQueueManager.getInstance(context);
		calendar = LocalCalendars.create(context.getContentResolver(), "Mine", "#00ff00", true, true, true);

		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		day = format.format(new Date(System.currentTimeMillis() + 2 * DAY));
	}

	@After
	public void tearDown() {
		if ( service != null ) service.stop();
	}

	private String event( String uid, String summary, String startTime ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "DTSTART:" + day + "T" + startTime + "Z\r\nDTEND:" + day + "T235900Z\r\n"
				+ "SUMMARY:" + summary + "\r\n"
				+ "BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:" + summary + "\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\n"
				+ "END:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	private long saveNew( String data ) throws Exception {
		long id = Saving.send(context, calendar, -1, data, RRResourceEditedRequest.ACTION_CREATE);
		Wait.untilThereAreNone("pending_change", null);
		Wait.untilThereAre(1, "alarms", "rid = " + id);
		waitForTheQueue();
		return id;
	}

	private void edit( long id, String data ) throws Exception {
		Saving.send(context, calendar, id, data, RRResourceEditedRequest.ACTION_UPDATE);
		Wait.untilThereAreNone("pending_change", null);
		// The queue hears of the change from the resource manager's thread
		Thread.sleep(500);
		waitForTheQueue();
	}

	/** Requests are dealt with in the order they are sent, so this returns once all before it are done. */
	private void waitForTheQueue() {
		alarms.sendBlockingRequest(new BlockingAlarmRequest() {
			private volatile boolean processed = false;

			@Override
			public void process( AlarmTableManager processor ) {
				processed = true;
			}

			@Override
			public boolean isProcessed() {
				return processed;
			}

			@Override
			public String getLogDescription() {
				return "A test waiting for the requests before it";
			}
		});
	}

	private void rebuild() {
		alarms.sendRequest(new ARRebuildRequest());
		waitForTheQueue();
	}

	private static AlarmRow theAlarmOf( long resourceId ) throws Exception {
		SQLiteDatabase db = TestDatabase.openAsItIs();
		try {
			Cursor c = db.rawQuery("SELECT _id, base_ttf, ttf, rid, rrid, state, blob FROM alarms WHERE rid = " + resourceId,
					(String[]) null);
			try {
				assertEquals("alarms for resource " + resourceId, 1, c.getCount());
				c.moveToFirst();
				return new AlarmRow(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4),
						ALARM_STATE.values()[c.getInt(5)], c.getString(6));
			}
			finally {
				c.close();
			}
		}
		finally {
			db.close();
		}
	}

	private static long alarmsIn( ALARM_STATE state, long resourceId ) throws Exception {
		return TestDatabase.countNow("alarms", "rid = " + resourceId + " AND state = " + state.ordinal());
	}

	private void set( AlarmRow row, ALARM_STATE state ) {
		alarms.sendBlockingRequest(new ARUpdateAlarmState(row, state));
	}

	@Test
	public void anEventWithAnAlarmHasOneQueuedForBeforeItStarts() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));

		AlarmRow alarm = theAlarmOf(id);
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		long start = format.parse(day + "T100000").getTime();
		assertEquals("A quarter of an hour before the event", start - 15 * MINUTE, alarm.getTimeToFire());
		assertEquals(alarm.getTimeToFire(), alarm.baseTimeToFire);
		assertEquals(1, alarmsIn(ALARM_STATE.PENDING, id));
	}

	@Test
	public void editingAnEventDoesNotQueueItsAlarmTwice() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));

		edit(id, event("one@example.net", "Changed", "100000"));
		edit(id, event("one@example.net", "Changed again", "100000"));

		assertEquals(1, TestDatabase.countNow("alarms", "rid = " + id));
	}

	@Test
	public void movingAnEventMovesItsAlarm() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));
		long before = theAlarmOf(id).getTimeToFire();

		edit(id, event("one@example.net", "One", "120000"));

		assertEquals(before + 120 * MINUTE, theAlarmOf(id).getTimeToFire());
	}

	@Test
	public void aDismissedAlarmStaysDismissedWhenItsEventIsEdited() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));
		set(theAlarmOf(id), ALARM_STATE.DISMISSED);
		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, id));

		edit(id, event("one@example.net", "Changed", "100000"));

		assertEquals("It must not come back as an alarm still to go off", 0, alarmsIn(ALARM_STATE.PENDING, id));
		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, id));
	}

	@Test
	public void makingTheQueueAgainKeepsWhatTheUserDidWithEachAlarm() throws Exception {
		long dismissed = saveNew(event("one@example.net", "One", "100000"));
		long snoozed = saveNew(event("two@example.net", "Two", "110000"));
		long fired = saveNew(event("three@example.net", "Three", "120000"));
		long untouched = saveNew(event("four@example.net", "Four", "130000"));
		set(theAlarmOf(dismissed), ALARM_STATE.DISMISSED);
		set(theAlarmOf(snoozed), ALARM_STATE.SNOOZED);
		set(theAlarmOf(fired), ALARM_STATE.FIRED);
		long snoozedUntil = theAlarmOf(snoozed).getTimeToFire();

		rebuild();
		rebuild();

		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, dismissed));
		assertEquals(1, alarmsIn(ALARM_STATE.SNOOZED, snoozed));
		assertEquals(1, alarmsIn(ALARM_STATE.FIRED, fired));
		assertEquals(1, alarmsIn(ALARM_STATE.PENDING, untouched));
		assertEquals("Each alarm is there once", 4, TestDatabase.countNow("alarms", null));
		assertEquals(snoozedUntil, theAlarmOf(snoozed).getTimeToFire());
	}

	@Test
	public void aSnoozedAlarmGoesOffNineMinutesAfterItWouldHave() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));
		AlarmRow alarm = theAlarmOf(id);
		long original = alarm.getTimeToFire();

		set(alarm, ALARM_STATE.SNOOZED);

		AlarmRow snoozed = theAlarmOf(id);
		assertEquals(original + 9 * MINUTE, snoozed.getTimeToFire());
		assertEquals("What it was first set for is remembered", original, snoozed.baseTimeToFire);
		assertEquals(1, alarmsIn(ALARM_STATE.SNOOZED, id));
	}

	@Test
	public void anAlarmIsFoundByWhatItIsWhenItsRowIdIsOutOfDate() throws Exception {
		long id = saveNew(event("one@example.net", "One", "100000"));
		AlarmRow shown = theAlarmOf(id);
		// The queue is made again while the notification is showing, and the row gets a new id
		rebuild();
		assertTrue("The test needs the id to have changed", theAlarmOf(id).getId() != shown.getId());

		set(shown, ALARM_STATE.DISMISSED);

		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, id));
		assertEquals(1, TestDatabase.countNow("alarms", "rid = " + id));
	}

	/**
	 * An alarm comes back from its notification with a stale row id, and with
	 * an empty string where it had no recurrence id, or none where it had an
	 * empty one.  It is the same alarm.
	 */
	@Test
	public void anAlarmIsFoundWhetherItsRecurrenceIdIsMissingOrEmpty() throws Exception {
		long none = saveNew(event("one@example.net", "One", "100000"));
		long empty = saveNew(event("two@example.net", "Two", "110000"));
		SQLiteDatabase db = TestDatabase.open();
		db.execSQL("UPDATE alarms SET rrid = NULL WHERE rid = " + none);
		db.execSQL("UPDATE alarms SET rrid = '' WHERE rid = " + empty);
		db.close();

		AlarmRow stored = theAlarmOf(none);
		set(new AlarmRow(stored.getId() + 1000, stored.baseTimeToFire, stored.getTimeToFire(),
				stored.resourceId, "", ALARM_STATE.FIRED, stored.getBlob()), ALARM_STATE.DISMISSED);
		stored = theAlarmOf(empty);
		set(new AlarmRow(stored.getId() + 1000, stored.baseTimeToFire, stored.getTimeToFire(),
				stored.resourceId, null, ALARM_STATE.FIRED, stored.getBlob()), ALARM_STATE.DISMISSED);

		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, none));
		assertEquals(1, alarmsIn(ALARM_STATE.DISMISSED, empty));
	}

	@Test
	public void theQueueIsMarkedAsInOrderOnceItHasStarted() throws Exception {
		saveNew(event("one@example.net", "One", "100000"));

		assertEquals("One row says whether the queue can be trusted", 1, TestDatabase.countNow("alarm_meta", null));
		assertEquals(1, TestDatabase.countNow("alarm_meta", "closed = 1"));
	}
}
