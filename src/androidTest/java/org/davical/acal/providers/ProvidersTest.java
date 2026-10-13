package org.davical.acal.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.database.TestDatabase;
import org.davical.acal.storage.BackendType;
import org.davical.acal.storage.LocalCalendars;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The servers and collections as the rest of the app reaches them: through
 * the content providers, against the real database.
 */
@RunWith(AndroidJUnit4.class)
public class ProvidersTest {

	private Context context;
	private ContentResolver cr;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		context = TestDatabase.context();
		cr = context.getContentResolver();
	}

	private int addServer( String name ) {
		ContentValues server = new ContentValues();
		server.put(Servers.FRIENDLY_NAME, name);
		server.put(Servers.HOSTNAME, name + ".example.net");
		server.put(Servers.USERNAME, "someone");
		server.put(Servers.PASSWORD, "secret");
		server.put(Servers.ACTIVE, 1);
		return (int) ContentUris.parseId(cr.insert(Servers.CONTENT_URI, server));
	}

	private long addCollection( int serverId, String path, String name ) {
		ContentValues collection = new ContentValues();
		collection.put(DavCollections.SERVER_ID, serverId);
		collection.put(DavCollections.COLLECTION_PATH, path);
		collection.put(DavCollections.DISPLAYNAME, name);
		collection.put(DavCollections.HOLDS_EVENTS, 1);
		collection.put(DavCollections.ACTIVE_EVENTS, 1);
		return ContentUris.parseId(cr.insert(DavCollections.CONTENT_URI, collection));
	}

	private int count( Uri uri, String selection ) {
		Cursor c = cr.query(uri, null, selection, null, null);
		try {
			return c.getCount();
		}
		finally {
			c.close();
		}
	}

	@Test
	public void aNewInstallHasOnlyTheAccountForWhatIsKeptOnThePhone() {
		assertEquals(1, count(Servers.CONTENT_URI, null));
		int accountId = LocalCalendars.accountId(cr);
		ContentValues account = Servers.getRow(accountId, cr);
		assertEquals(BackendType.LOCAL, BackendType.fromServerRow(account));
		assertTrue(LocalCalendars.isLocalAccount(account));
		assertEquals(0, count(DavCollections.CONTENT_URI, null));
	}

	@Test
	public void aServerIsStoredAsCalDavAndReadBack() {
		int id = addServer("work");

		ContentValues server = Servers.getRow(id, cr);
		assertEquals("work", server.getAsString(Servers.FRIENDLY_NAME));
		assertEquals("work.example.net", server.getAsString(Servers.HOSTNAME));
		assertEquals(BackendType.CALDAV, BackendType.fromServerRow(server));
		assertFalse(LocalCalendars.isLocalAccount(server));
		assertNotEquals(id, LocalCalendars.accountId(cr));
	}

	@Test
	public void aPasswordIsNotStoredAsItWasTypedAndComesBackAsItWas() {
		int id = addServer("work");

		assertNotEquals("secret", Servers.getRow(id, cr).getAsString(Servers.PASSWORD));
		assertEquals("secret", Servers.getRowDecrypted(id, context).getAsString(Servers.PASSWORD));

		ContentValues change = new ContentValues();
		change.put(Servers.PASSWORD, "another");
		assertEquals(1, cr.update(ContentUris.withAppendedId(Servers.CONTENT_URI, id), change, null, null));

		assertNotEquals("another", Servers.getRow(id, cr).getAsString(Servers.PASSWORD));
		assertEquals("another", Servers.getRowDecrypted(id, context).getAsString(Servers.PASSWORD));
	}

	@Test
	public void aServerIsChangedAndDeletedByItsId() {
		int work = addServer("work");
		int home = addServer("home");

		ContentValues change = new ContentValues();
		change.put(Servers.FRIENDLY_NAME, "the office");
		assertEquals(1, cr.update(ContentUris.withAppendedId(Servers.CONTENT_URI, work), change, null, null));
		assertEquals("the office", Servers.getRow(work, cr).getAsString(Servers.FRIENDLY_NAME));
		assertEquals("home", Servers.getRow(home, cr).getAsString(Servers.FRIENDLY_NAME));

		assertEquals(1, cr.delete(ContentUris.withAppendedId(Servers.CONTENT_URI, work), null, null));
		assertNull(Servers.getRow(work, cr));
		assertNotNull(Servers.getRow(home, cr));
	}

	@Test
	public void aCollectionIsReadBackByItsIdAndByItsServer() {
		int work = addServer("work");
		int home = addServer("home");
		long calendar = addCollection(work, "/work/calendar/", "Work");
		addCollection(work, "/work/tasks/", "Tasks");
		addCollection(home, "/home/calendar/", "Home");

		ContentValues row = DavCollections.getRow(calendar, cr);
		assertEquals("Work", row.getAsString(DavCollections.DISPLAYNAME));
		assertEquals("/work/calendar/", row.getAsString(DavCollections.COLLECTION_PATH));
		assertEquals(work, (int) row.getAsInteger(DavCollections.SERVER_ID));

		assertEquals(3, count(DavCollections.CONTENT_URI, null));
		assertEquals(2, count(Uri.withAppendedPath(DavCollections.CONTENT_URI, "server/" + work), null));
		assertEquals(1, count(Uri.withAppendedPath(DavCollections.CONTENT_URI, "server/" + home), null));
	}

	@Test
	public void aCollectionIsChangedAndDeletedByItsId() {
		int work = addServer("work");
		long calendar = addCollection(work, "/work/calendar/", "Work");
		long tasks = addCollection(work, "/work/tasks/", "Tasks");
		Uri calendarUri = ContentUris.withAppendedId(DavCollections.CONTENT_URI, calendar);

		ContentValues change = new ContentValues();
		change.put(DavCollections.DISPLAYNAME, "Office");
		assertEquals(1, cr.update(calendarUri, change, null, null));
		assertEquals("Office", DavCollections.getRow(calendar, cr).getAsString(DavCollections.DISPLAYNAME));
		assertEquals("Tasks", DavCollections.getRow(tasks, cr).getAsString(DavCollections.DISPLAYNAME));

		assertEquals(1, cr.delete(calendarUri, null, null));
		assertNull(DavCollections.getRow(calendar, cr));
		assertNotNull(DavCollections.getRow(tasks, cr));
	}

	@Test
	public void aCollectionThatIsNotThereIsNotMistakenForAnother() {
		int work = addServer("work");
		addCollection(work, "/work/calendar/", "Work");

		assertNull(DavCollections.getRow(-1, cr));
		assertNull(DavCollections.getRow(9999, cr));
	}

	@Test
	public void twoCollectionsOfAServerCannotHaveTheSamePath() {
		int work = addServer("work");
		addCollection(work, "/work/calendar/", "Work");
		try {
			addCollection(work, "/work/calendar/", "Again");
		}
		catch( RuntimeException expected ) {
		}
		assertEquals(1, count(DavCollections.CONTENT_URI, null));
	}

	@Test
	public void aCalendarKeptOnThePhoneBelongsToThePhonesAccount() {
		int work = addServer("work");
		long remote = addCollection(work, "/work/calendar/", "Work");

		long local = LocalCalendars.create(cr, "Mine", "#00ff00", true, true, false);

		ContentValues row = DavCollections.getRow(local, cr);
		assertEquals("Mine", row.getAsString(DavCollections.DISPLAYNAME));
		assertEquals(LocalCalendars.accountId(cr), (int) row.getAsInteger(DavCollections.SERVER_ID));
		assertTrue(LocalCalendars.isLocal(row, cr));
		assertFalse(LocalCalendars.isLocal(DavCollections.getRow(remote, cr), cr));
		assertEquals(1, (int) row.getAsInteger(DavCollections.ACTIVE_EVENTS));
		assertEquals(1, (int) row.getAsInteger(DavCollections.ACTIVE_TASKS));
		assertEquals(0, (int) row.getAsInteger(DavCollections.ACTIVE_JOURNAL));
	}

	@Test
	public void makingSureThereIsALocalCalendarMakesOnlyOne() {
		LocalCalendars.ensureOne(cr, "My calendar");
		LocalCalendars.ensureOne(cr, "My calendar");

		assertEquals(1, count(DavCollections.CONTENT_URI, null));
	}
}
