package org.davical.acal.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.AcalApplication;
import org.davical.acal.database.TestDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The time zones aCal has been told of, with the other names each goes by,
 * and where on each server its calendars are to be found.
 */
@RunWith(AndroidJUnit4.class)
public class TimezonesAndPathSetsTest {

	private ContentResolver cr;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		cr = TestDatabase.context().getContentResolver();
	}

	private int count( Uri uri ) {
		Cursor c = cr.query(uri, null, null, null, null);
		try {
			return c.getCount();
		}
		finally {
			c.close();
		}
	}

	// ---- Time zones ---------------------------------------------------

	private void addZone( String tzid, String aliases ) {
		ContentValues zone = new ContentValues();
		zone.put(Timezones.TZID, tzid);
		zone.put(Timezones.DEFAULT_NAME, tzid);
		zone.put(Timezones.LAST_MODIFIED, 20260101L);
		zone.put(Timezones.ZONE_DATA, "BEGIN:VTIMEZONE\r\nTZID:" + tzid + "\r\nEND:VTIMEZONE\r\n");
		zone.put(Timezones.TZID_ALIASES, aliases);
		zone.put(Timezones.TZ_NAMES, "en~" + tzid + "\nfr~" + tzid + " (fr)");
		cr.insert(Timezones.CONTENT_URI, zone);
	}

	private String resolve( String name ) {
		Cursor c = cr.query(Uri.withAppendedPath(Uri.withAppendedPath(Timezones.CONTENT_URI, "resolve_alias"),
				Uri.encode(name)), null, null, null, null);
		try {
			return c.moveToFirst() ? c.getString(0) : null;
		}
		finally {
			c.close();
		}
	}

	@Test
	public void aZoneIsStoredWithItsNamesAndAliases() throws Exception {
		addZone("Pacific/Auckland", "NZ\nNew Zealand Standard Time");

		assertEquals(1, count(Timezones.CONTENT_URI));
		assertEquals(1, TestDatabase.countNow("timezone", "tzid = 'Pacific/Auckland' AND zone_data LIKE 'BEGIN:VTIMEZONE%'"));
		assertEquals(2, TestDatabase.countNow("timezone_alias", "tzid = 'Pacific/Auckland'"));
		assertEquals(1, TestDatabase.countNow("timezone_name", "tzid = 'Pacific/Auckland' AND locale = 'fr'"
				+ " AND tzname = 'Pacific/Auckland (fr)'"));
	}

	/** Calendars made on Windows name zones in its own way, which only this table can turn into ours. */
	@Test
	public void anotherNameForAZoneLeadsToTheZone() {
		addZone("Pacific/Auckland", "NZ\nNew Zealand Standard Time");
		addZone("Europe/London", "GB\nGMT Standard Time");

		assertEquals("Pacific/Auckland", resolve("New Zealand Standard Time"));
		assertEquals("Pacific/Auckland", resolve("NZ"));
		assertEquals("Europe/London", resolve("GMT Standard Time"));
		assertEquals("Pacific/Auckland", AcalApplication.getOlsonFromAlias("New Zealand Standard Time"));
	}

	@Test
	public void aNameNoZoneGoesByLeadsNowhere() {
		addZone("Pacific/Auckland", "NZ");

		assertNull(resolve("Nowhere Standard Time"));
		assertNull(AcalApplication.getOlsonFromAlias("Nowhere Standard Time"));
	}

	// ---- Where the calendars are --------------------------------------

	private void addPath( int serverId, int type, String path ) {
		ContentValues set = new ContentValues();
		set.put(PathSets.SERVER_ID, serverId);
		set.put(PathSets.SET_TYPE, type);
		set.put(PathSets.PATH, path);
		cr.insert(PathSets.CONTENT_URI, set);
	}

	private static Uri pathsOf( int serverId ) {
		return Uri.parse(PathSets.CONTENT_URI.toString() + "/servers/" + serverId);
	}

	private int transaction( String step ) {
		return cr.update(Uri.withAppendedPath(PathSets.CONTENT_URI, step), new ContentValues(), "", new String[0]);
	}

	@Test
	public void pathsAreKeptForEachServerAndEachKind() {
		addPath(1, PathSets.CALENDAR_HOME_SET, "/one/calendars/");
		addPath(1, PathSets.ADDRESSBOOK_HOME_SET, "/one/contacts/");
		addPath(2, PathSets.CALENDAR_HOME_SET, "/two/calendars/");

		assertEquals(2, count(pathsOf(1)));
		assertEquals(1, count(pathsOf(2)));
		assertEquals(1, count(Uri.parse(pathsOf(1) + "/type/" + PathSets.CALENDAR_HOME_SET)));

		assertEquals(2, cr.delete(pathsOf(1), null, null));
		assertEquals(0, count(pathsOf(1)));
		assertEquals(1, count(pathsOf(2)));
	}

	/**
	 * Looking for where a server's calendars are deletes what was known and
	 * stores what is found, and when that goes wrong part way what was known
	 * has to still be there.
	 */
	@Test
	public void aChangeOfPathsThatIsNotApprovedIsUndone() {
		addPath(1, PathSets.CALENDAR_HOME_SET, "/one/calendars/");

		assertEquals(1, transaction("begin"));
		cr.delete(pathsOf(1), null, null);
		addPath(1, PathSets.CALENDAR_HOME_SET, "/one/somewhere-else/");
		assertEquals(1, transaction("commit"));

		assertEquals(1, count(pathsOf(1)));
		Cursor c = cr.query(pathsOf(1), null, null, null, null);
		c.moveToFirst();
		assertEquals("/one/calendars/", c.getString(c.getColumnIndex(PathSets.PATH)));
		c.close();
	}

	@Test
	public void aChangeOfPathsThatIsApprovedIsKept() {
		addPath(1, PathSets.CALENDAR_HOME_SET, "/one/calendars/");

		assertEquals(1, transaction("begin"));
		cr.delete(pathsOf(1), null, null);
		addPath(1, PathSets.CALENDAR_HOME_SET, "/one/somewhere-else/");
		assertEquals(1, transaction("approve"));
		assertEquals(1, transaction("commit"));

		Cursor c = cr.query(pathsOf(1), null, null, null, null);
		assertEquals(1, c.getCount());
		c.moveToFirst();
		assertEquals("/one/somewhere-else/", c.getString(c.getColumnIndex(PathSets.PATH)));
		c.close();
	}

	@Test
	public void oneChangeOfPathsCannotBeStartedInsideAnother() {
		assertEquals(1, transaction("begin"));
		assertEquals("A second is refused", 0, transaction("begin"));
		assertEquals(1, transaction("commit"));
		assertEquals("There is none left to end", 0, transaction("commit"));
	}
}
