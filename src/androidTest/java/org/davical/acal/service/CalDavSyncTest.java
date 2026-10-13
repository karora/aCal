package org.davical.acal.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.davical.acal.RunningService;
import org.davical.acal.Wait;
import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.TestDatabase;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.davacal.VComponent;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.PathSets;
import org.davical.acal.providers.Servers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Synchronising a calendar with a CalDAV server, both ways, with the real
 * database and the real jobs against a server on this device.
 */
@RunWith(AndroidJUnit4.class)
public class CalDavSyncTest {

	private Context context;
	private FakeCalDavServer server;
	private RunningService service;
	private int serverId;
	private long calendar;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		server = new FakeCalDavServer();
		server.start(context);
	}

	@After
	public void tearDown() {
		if ( service != null ) service.stop();
		if ( server != null ) server.stop();
	}

	/** The account and its one calendar, as they are once a server has been set up. */
	private void addTheAccount( boolean serverHasSyncReport ) throws Exception {
		ContentValues account = new ContentValues();
		account.put(Servers.FRIENDLY_NAME, "test");
		account.put(Servers.HOSTNAME, FakeCalDavServer.HOST);
		account.put(Servers.PORT, server.port());
		account.put(Servers.USE_SSL, 1);
		account.put(Servers.SUPPLIED_USER_URL, "https://" + FakeCalDavServer.HOST + ":" + server.port() + FakeCalDavServer.PRINCIPAL);
		account.put(Servers.PRINCIPAL_PATH, FakeCalDavServer.PRINCIPAL);
		account.put(Servers.AUTH_TYPE, Servers.AUTH_BASIC);
		account.put(Servers.USERNAME, FakeCalDavServer.USERNAME);
		account.put(Servers.PASSWORD, FakeCalDavServer.PASSWORD);
		account.put(Servers.HAS_CALDAV, 1);
		account.put(Servers.HAS_MULTIGET, 1);
		account.put(Servers.HAS_SYNC, serverHasSyncReport ? 1 : 0);
		account.put(Servers.ACTIVE, 1);
		serverId = (int) ContentUris.parseId(context.getContentResolver().insert(Servers.CONTENT_URI, account));

		ContentValues collection = new ContentValues();
		collection.put(DavCollections.SERVER_ID, serverId);
		// As it is stored when the calendar is found on the server: the whole URL
		collection.put(DavCollections.COLLECTION_PATH, server.url(FakeCalDavServer.CALENDAR));
		collection.put(DavCollections.DISPLAYNAME, "Calendar");
		collection.put(DavCollections.HOLDS_EVENTS, 1);
		collection.put(DavCollections.HOLDS_TASKS, 1);
		collection.put(DavCollections.HOLDS_JOURNAL, 1);
		collection.put(DavCollections.HOLDS_ADDRESSBOOK, 0);
		collection.put(DavCollections.ACTIVE_EVENTS, 1);
		collection.put(DavCollections.ACTIVE_TASKS, 1);
		collection.put(DavCollections.ACTIVE_JOURNAL, 1);
		collection.put(DavCollections.ACTIVE_ADDRESSBOOK, 0);
		collection.put(DavCollections.NEEDS_SYNC, 1);
		collection.put(DavCollections.MAX_SYNC_AGE_WIFI, 3600000);
		collection.put(DavCollections.MAX_SYNC_AGE_3G, 3600000);
		collection.put(DavCollections.IS_WRITABLE, 1);
		collection.put(DavCollections.IS_VISIBLE, 1);
		collection.put(DavCollections.USE_ALARMS, 1);
		calendar = ContentUris.parseId(context.getContentResolver().insert(DavCollections.CONTENT_URI, collection));

		ContentValues homeSet = new ContentValues();
		homeSet.put(PathSets.SERVER_ID, serverId);
		homeSet.put(PathSets.SET_TYPE, PathSets.CALENDAR_HOME_SET);
		homeSet.put(PathSets.PATH, FakeCalDavServer.HOME);
		context.getContentResolver().insert(PathSets.CONTENT_URI, homeSet);

		service = RunningService.start(context);
	}

	private static String event( String uid, String summary ) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//aCal//test//EN\r\n"
				+ "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n"
				+ "DTSTART:20270315T100000Z\r\nDTEND:20270315T110000Z\r\n"
				+ "SUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
	}

	private void syncNow() throws Exception {
		service.requests().syncCollectionNow(calendar);
	}

	/** Synchronise, and wait until the phone has the sync-token the server is now at. */
	private void syncAndWait() throws Exception {
		final String token = server.syncToken();
		syncNow();
		Wait.until("the calendar has synchronised to " + token, new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return token.equals(syncTokenOnThePhone());
			}
		});
	}

	private String syncTokenOnThePhone() throws Exception {
		return TestDatabase.stringNow("SELECT sync_token FROM dav_collection WHERE _id = " + calendar);
	}

	private String dataOnThePhone( String name ) throws Exception {
		return TestDatabase.stringNow("SELECT data FROM dav_resource WHERE collection_id = " + calendar
				+ " AND name = '" + name + "'");
	}

	private String etagOnThePhone( String name ) throws Exception {
		return TestDatabase.stringNow("SELECT etag FROM dav_resource WHERE collection_id = " + calendar
				+ " AND name = '" + name + "'");
	}

	private long idOnThePhone( String name ) throws Exception {
		return Long.parseLong(TestDatabase.stringNow("SELECT _id FROM dav_resource WHERE collection_id = " + calendar
				+ " AND name = '" + name + "'"));
	}

	private long resourcesOnThePhone() throws Exception {
		return TestDatabase.countNow("dav_resource", "collection_id = " + calendar);
	}

	private void waitUntilThePhoneHas( final String name, final String summary ) throws Exception {
		Wait.until("the phone has " + name + " with " + summary, new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				String data = dataOnThePhone(name);
				return data != null && data.contains("SUMMARY:" + summary);
			}
		});
	}

	/** Send what a screen sends when the user saves, and wait for the answer it gets. */
	private long save( long resourceId, String data, int action ) throws Exception {
		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicReference<Long> answer = new AtomicReference<Long>();
		ResourceManager.getInstance(context).sendRequest(new RRResourceEditedRequest(
				new ResourceResponseListener<Long>() {
					@Override
					public void resourceResponse( ResourceResponse<Long> response ) {
						answer.set(response.result());
						answered.countDown();
					}
				}, calendar, resourceId, VComponent.createComponentFromBlob(data), action));
		assertTrue("The save was not answered", answered.await(30, TimeUnit.SECONDS));
		assertNotNull("The save was refused", answer.get());
		return answer.get();
	}

	// ---- From the server to the phone ---------------------------------

	@Test
	public void theFirstSyncFetchesEverythingOnTheServer() throws Exception {
		String etagOne = server.put("one.ics", event("one@example.net", "One"));
		server.put("two.ics", event("two@example.net", "Two"));
		addTheAccount(true);

		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		waitUntilThePhoneHas("two.ics", "Two");

		assertEquals(2, resourcesOnThePhone());
		assertEquals(etagOne, etagOnThePhone("one.ics"));
		assertEquals(0, TestDatabase.countNow("dav_resource", "needs_sync = 1"));
		assertEquals("VEVENT", TestDatabase.stringNow("SELECT effective_type FROM dav_resource WHERE name = 'one.ics'"));
		assertEquals("Nothing is waiting to be sent back", 0, TestDatabase.countNow("pending_change", null));
	}

	@Test
	public void aLaterSyncAsksOnlyForWhatChangedAndAppliesIt() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		server.put("two.ics", event("two@example.net", "Two"));
		server.put("three.ics", event("three@example.net", "Three"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("three.ics", "Three");
		String firstToken = syncTokenOnThePhone();

		server.put("one.ics", event("one@example.net", "Changed"));
		server.put("four.ics", event("four@example.net", "Four"));
		server.delete("two.ics");
		server.forgetWhatWasAsked();
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "Changed");
		waitUntilThePhoneHas("four.ics", "Four");
		Wait.untilThereAreNone("dav_resource", "name = 'two.ics'");

		assertEquals(3, resourcesOnThePhone());
		assertTrue(dataOnThePhone("three.ics").contains("SUMMARY:Three"));
		assertEquals(server.etag("one.ics"), etagOnThePhone("one.ics"));

		FakeCalDavServer.Asked report = null;
		for( FakeCalDavServer.Asked each : server.asked("REPORT") ) {
			if ( each.body.contains("sync-collection") ) {
				report = each;
				break;
			}
		}
		assertNotNull("It asked for the changes since the last sync", report);
		assertTrue(report.body, report.body.contains("<sync-token>" + firstToken + "</sync-token>"));
		for( FakeCalDavServer.Asked each : server.asked("REPORT") ) {
			if ( each.body.contains("calendar-multiget") ) {
				assertTrue("What had not changed was fetched again:\n" + each.body, !each.body.contains("three.ics"));
			}
		}
	}

	@Test
	public void aSyncWithNothingChangedLeavesEverythingAsItWas() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		final String firstSync = TestDatabase.stringNow("SELECT last_synchronised FROM dav_collection WHERE _id = " + calendar);
		String token = syncTokenOnThePhone();

		Thread.sleep(1100);
		server.forgetWhatWasAsked();
		syncNow();
		Wait.until("the calendar is marked as synchronised again", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				String now = TestDatabase.stringNow("SELECT last_synchronised FROM dav_collection WHERE _id = " + calendar);
				return now != null && !now.equals(firstSync);
			}
		});

		assertEquals(token, syncTokenOnThePhone());
		assertEquals(1, resourcesOnThePhone());
		assertTrue(dataOnThePhone("one.ics").contains("SUMMARY:One"));
		for( FakeCalDavServer.Asked each : server.asked("REPORT") ) {
			assertTrue("Nothing had changed, and something was fetched:\n" + each.body,
					!each.body.contains("calendar-multiget"));
		}
	}

	@Test
	public void whenTheServerRefusesTheSyncTokenEverythingIsComparedInstead() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		server.put("two.ics", event("two@example.net", "Two"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("two.ics", "Two");
		final String staleToken = syncTokenOnThePhone();

		server.put("one.ics", event("one@example.net", "Changed"));
		server.delete("two.ics");
		server.put("three.ics", event("three@example.net", "Three"));
		server.forgetSyncTokens();
		syncNow();

		waitUntilThePhoneHas("one.ics", "Changed");
		waitUntilThePhoneHas("three.ics", "Three");
		Wait.untilThereAreNone("dav_resource", "name = 'two.ics'");
		assertEquals(2, resourcesOnThePhone());

		// The token that was refused must not come back, or every sync from
		// now on is refused the same way
		Wait.until("the refused token is gone", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return !staleToken.equals(syncTokenOnThePhone());
			}
		});
		server.put("four.ics", event("four@example.net", "Four"));
		syncAndWait();
		waitUntilThePhoneHas("four.ics", "Four");
	}

	@Test
	public void aServerWithoutSyncReportsIsComparedByEtag() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		server.put("two.ics", event("two@example.net", "Two"));
		addTheAccount(false);
		syncNow();
		waitUntilThePhoneHas("one.ics", "One");
		waitUntilThePhoneHas("two.ics", "Two");

		server.put("one.ics", event("one@example.net", "Changed"));
		server.delete("two.ics");
		server.put("three.ics", event("three@example.net", "Three"));
		syncNow();

		waitUntilThePhoneHas("one.ics", "Changed");
		waitUntilThePhoneHas("three.ics", "Three");
		Wait.untilThereAreNone("dav_resource", "name = 'two.ics'");
		assertEquals(2, resourcesOnThePhone());
		assertEquals(0, server.asked("REPORT").stream().filter(a -> a.body.contains("sync-collection")).count());
	}

	@Test
	public void aServerWithoutSyncReportsIsNotListedAgainWhileItsCtagIsTheSame() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(false);
		syncNow();
		waitUntilThePhoneHas("one.ics", "One");
		Wait.until("the ctag is remembered", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return TestDatabase.stringNow("SELECT collection_tag FROM dav_collection WHERE _id = " + calendar) != null
						&& TestDatabase.stringNow("SELECT last_synchronised FROM dav_collection WHERE _id = " + calendar) != null;
			}
		});
		final String firstSync = TestDatabase.stringNow("SELECT last_synchronised FROM dav_collection WHERE _id = " + calendar);

		Thread.sleep(1100);
		server.forgetWhatWasAsked();
		syncNow();
		Wait.until("the calendar is marked as synchronised again", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				String now = TestDatabase.stringNow("SELECT last_synchronised FROM dav_collection WHERE _id = " + calendar);
				return now != null && !now.equals(firstSync);
			}
		});

		for( FakeCalDavServer.Asked each : server.asked("PROPFIND") ) {
			assertEquals("Everything was listed though the ctag said nothing had changed", "0", each.header("Depth"));
		}
		assertEquals(0, server.asked("REPORT").size());
	}

	@Test
	public void twoSyncsAskedForAtOnceDoNotStopLaterOnes() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);

		syncNow();
		syncNow();
		service.requests().fullCollectionResync(calendar);
		syncNow();
		waitUntilThePhoneHas("one.ics", "One");

		server.put("two.ics", event("two@example.net", "Two"));
		syncAndWait();
		waitUntilThePhoneHas("two.ics", "Two");
		assertEquals(2, resourcesOnThePhone());
	}

	@Test
	public void aResourceThatHasGoneByTheTimeItIsFetchedIsNotKept() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");

		// Listed as new by one request, and deleted before the next fetches it
		server.put("two.ics", event("two@example.net", "Two"));
		server.put("vanished.ics", event("vanished@example.net", "Vanished"));
		server.deleteJustBeforeItIsFetched("vanished.ics");
		syncNow();
		waitUntilThePhoneHas("two.ics", "Two");
		// The server now says it has been deleted
		syncAndWait();

		Wait.untilThereAreNone("dav_resource", "name = 'vanished.ics'");
		assertEquals(2, resourcesOnThePhone());
		assertEquals("Nothing is left to be asked for again at every sync", 0,
				TestDatabase.countNow("dav_resource", "needs_sync = 1"));
	}

	// ---- The calendars of an account ----------------------------------

	private void updateCalendarList() throws Exception {
		service.requests().updateCollectionsFromHomeSets();
	}

	@Test
	public void aCalendarMadeElsewhereIsFoundWithWhatTheServerSaysOfIt() throws Exception {
		addTheAccount(true);
		server.addCalendar(FakeCalDavServer.HOME + "holidays/", "Holidays");

		updateCalendarList();

		final String url = server.url(FakeCalDavServer.HOME + "holidays/");
		Wait.untilThereAre(1, "dav_collection", "collection_path = '" + url + "'");
		assertEquals(1, TestDatabase.countNow("dav_collection", "collection_path = '" + url + "'"
				+ " AND displayname = 'Holidays' AND server_id = " + serverId
				+ " AND holds_events = 1 AND holds_tasks = 1 AND holds_journal = 0 AND holds_addressbook = 0"
				+ " AND active_events = 1 AND is_writable = 1 AND colour = '#123456'"));
		assertEquals("The calendar that was there is still there, once", 1,
				TestDatabase.countNow("dav_collection", "_id = " + calendar));
	}

	@Test
	public void theHomeSetItselfAndThePrincipalsInItAreNotCalendars() throws Exception {
		addTheAccount(true);
		server.addCalendar(FakeCalDavServer.HOME + "holidays/", "Holidays");

		updateCalendarList();
		Wait.untilThereAre(1, "dav_collection", "displayname = 'Holidays'");

		assertEquals("Only the two calendars", 2, TestDatabase.countNow("dav_collection", "server_id = " + serverId));
		assertEquals(0, TestDatabase.countNow("dav_collection", "collection_path LIKE '%calendar-proxy-read/'"));
	}

	@Test
	public void aCalendarTheServerNoLongerListsGoesWithEverythingInIt() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		server.addCalendar(FakeCalDavServer.HOME + "holidays/", "Holidays");
		updateCalendarList();
		Wait.untilThereAre(1, "dav_collection", "displayname = 'Holidays'");

		server.removeCalendar();
		updateCalendarList();

		Wait.untilThereAreNone("dav_collection", "_id = " + calendar);
		Wait.untilThereAreNone("dav_resource", "collection_id = " + calendar);
		Wait.untilThereAreNone("event_cache", "collection_id = " + calendar);
		assertEquals("The one still listed stays", 1, TestDatabase.countNow("dav_collection", "displayname = 'Holidays'"));
	}

	@Test
	public void noCalendarIsRemovedWhenTheListCouldNotBeFetched() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		db("UPDATE dav_collection SET colour = '#abcdef' WHERE _id = " + calendar);

		server.refuse("PROPFIND", 503);
		updateCalendarList();
		Wait.until("the list was asked for", new Callable<Boolean>() {
			@Override
			public Boolean call() {
				for( FakeCalDavServer.Asked each : server.asked("PROPFIND") ) {
					if ( each.path.equals(FakeCalDavServer.HOME) ) return true;
				}
				return false;
			}
		});
		Thread.sleep(1000);

		assertEquals("The calendar and the user's settings for it are kept", 1,
				TestDatabase.countNow("dav_collection", "_id = " + calendar + " AND colour = '#abcdef'"));
		assertEquals(1, resourcesOnThePhone());
	}

	@Test
	public void aCalendarThatAnswersNotFoundIsCheckedAgainstTheListAndRemoved() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");

		server.removeCalendar();
		syncNow();

		Wait.untilThereAreNone("dav_collection", "_id = " + calendar);
		Wait.untilThereAreNone("dav_resource", "collection_id = " + calendar);
	}

	private static void db( String sql ) {
		SQLiteDatabase db = TestDatabase.open();
		try {
			db.execSQL(sql);
		}
		finally {
			db.close();
		}
	}

	// ---- From the phone to the server ---------------------------------

	@Test
	public void aNewEventIsSentAndOnlyIfTheServerHasNoneByThatName() throws Exception {
		addTheAccount(true);
		syncAndWait();

		final long id = save(-1, event("new@example.net", "New"), RRResourceEditedRequest.ACTION_CREATE);
		Wait.untilThereAreNone("pending_change", null);

		List<FakeCalDavServer.Asked> puts = server.asked("PUT");
		assertEquals(1, puts.size());
		assertEquals("*", puts.get(0).header("If-None-Match"));
		assertNull(puts.get(0).header("If-Match"));
		assertTrue(puts.get(0).header("Content-Type"), puts.get(0).header("Content-Type").startsWith("text/calendar"));
		assertEquals(1, server.names().size());
		final String name = server.names().get(0);
		assertTrue(server.data(name).contains("SUMMARY:New"));

		Wait.until("the phone knows the name and etag the server gave it", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return name.equals(TestDatabase.stringNow("SELECT name FROM dav_resource WHERE _id = " + id))
						&& server.etag(name).equals(TestDatabase.stringNow("SELECT etag FROM dav_resource WHERE _id = " + id));
			}
		});
		assertEquals("It is one event, not one sent and one fetched", 1, resourcesOnThePhone());
	}

	@Test
	public void anEditIsSentOnlyIfTheServerStillHasWhatWasEdited() throws Exception {
		String etag = server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");

		save(idOnThePhone("one.ics"), event("one@example.net", "Edited"), RRResourceEditedRequest.ACTION_UPDATE);
		Wait.untilThereAreNone("pending_change", null);

		List<FakeCalDavServer.Asked> puts = server.asked("PUT");
		assertEquals(1, puts.size());
		assertEquals(FakeCalDavServer.CALENDAR + "one.ics", puts.get(0).path);
		assertEquals(etag, puts.get(0).header("If-Match"));
		assertTrue(server.data("one.ics").contains("SUMMARY:Edited"));
		Wait.until("the phone has the etag of what it sent", new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return server.etag("one.ics").equals(etagOnThePhone("one.ics"));
			}
		});
		assertTrue(dataOnThePhone("one.ics").contains("SUMMARY:Edited"));
	}

	@Test
	public void aDeleteIsSentOnlyIfTheServerStillHasWhatWasDeleted() throws Exception {
		String etag = server.put("one.ics", event("one@example.net", "One"));
		server.put("two.ics", event("two@example.net", "Two"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");

		save(idOnThePhone("one.ics"), event("one@example.net", "One"), RRResourceEditedRequest.ACTION_DELETE);
		Wait.untilThereAreNone("pending_change", null);

		List<FakeCalDavServer.Asked> deletes = server.asked("DELETE");
		assertEquals(1, deletes.size());
		assertEquals(FakeCalDavServer.CALENDAR + "one.ics", deletes.get(0).path);
		assertEquals(etag, deletes.get(0).header("If-Match"));
		assertNull(server.data("one.ics"));
		Wait.untilThereAreNone("dav_resource", "name = 'one.ics'");
		assertEquals(1, resourcesOnThePhone());
	}

	@Test
	public void anEditTheServerRefusesIsKeptForTheUserToDecide() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		// Another client changes it, and the phone has not heard
		server.put("one.ics", event("one@example.net", "Theirs"));

		save(idOnThePhone("one.ics"), event("one@example.net", "Mine"), RRResourceEditedRequest.ACTION_UPDATE);

		Wait.untilThereAre(1, "pending_change", "state = 1 AND last_status = 412");
		assertTrue("The server keeps what the other client wrote", server.data("one.ics").contains("SUMMARY:Theirs"));
		assertTrue("The user's edit is not thrown away",
				TestDatabase.stringNow("SELECT new_data FROM pending_change").contains("SUMMARY:Mine"));
	}

	@Test
	public void aRefusedEditThatIsDiscardedLeavesWhatTheServerHas() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		server.put("one.ics", event("one@example.net", "Theirs"));
		save(idOnThePhone("one.ics"), event("one@example.net", "Mine"), RRResourceEditedRequest.ACTION_UPDATE);
		Wait.untilThereAre(1, "pending_change", "state = 1");
		long pending = Long.parseLong(TestDatabase.stringNow("SELECT _id FROM pending_change"));

		ResourceManager.getInstance(context).sendRequest(
				new org.davical.acal.database.resourcesmanager.requests.RRResolvePendingChange(pending, false));
		Wait.untilThereAreNone("pending_change", null);
		syncAndWait();

		waitUntilThePhoneHas("one.ics", "Theirs");
		assertEquals(1, server.asked("PUT").size());
	}

	@Test
	public void aChangeTheServerCannotTakeYetWaitsToBeTriedAgain() throws Exception {
		server.put("one.ics", event("one@example.net", "One"));
		addTheAccount(true);
		syncAndWait();
		waitUntilThePhoneHas("one.ics", "One");
		server.refuse("PUT", 503);

		save(idOnThePhone("one.ics"), event("one@example.net", "Edited"), RRResourceEditedRequest.ACTION_UPDATE);
		Wait.untilThereAre(1, "pending_change", "state = 0 AND attempts = 1 AND last_status = 503");
		assertTrue(server.data("one.ics").contains("SUMMARY:One"));

		// The user asks for a sync, which tries what is waiting without waiting any longer
		server.stopRefusing();
		syncNow();

		Wait.untilThereAreNone("pending_change", null);
		assertTrue(server.data("one.ics").contains("SUMMARY:Edited"));
	}
}
