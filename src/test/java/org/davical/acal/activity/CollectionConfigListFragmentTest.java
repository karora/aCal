package org.davical.acal.activity;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;
import org.davical.acal.storage.BackendType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/**
 * The list of calendars has to show what is there now when the user comes
 * back to it from the screen for one calendar.
 */
@RunWith(RobolectricTestRunner.class)
public class CollectionConfigListFragmentTest {

	private static final int ACCOUNT = 1;
	private static final List<Integer> calendars = new ArrayList<Integer>();

	/** Answers for either table: the one account, or the calendars there are now. */
	public static class RowsProvider extends ContentProvider {
		@Override
		public boolean onCreate() {
			return true;
		}

		@Override
		public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
			if ( Servers.AUTHORITY.equals(uri.getAuthority()) ) {
				MatrixCursor servers = new MatrixCursor(new String[] {
						Servers._ID, Servers.ACTIVE, Servers.FRIENDLY_NAME, Servers.BACKEND_TYPE });
				servers.addRow(new Object[] { ACCOUNT, 1, "This phone", BackendType.LOCAL.value });
				return servers;
			}
			MatrixCursor collections = new MatrixCursor(new String[] {
					DavCollections._ID, DavCollections.SERVER_ID, DavCollections.DISPLAYNAME, DavCollections.COLOUR });
			for( int id : calendars ) {
				collections.addRow(new Object[] { id, ACCOUNT, "Calendar " + id, "#ff0000" });
			}
			return collections;
		}

		@Override
		public String getType(Uri uri) {
			return null;
		}

		@Override
		public Uri insert(Uri uri, ContentValues values) {
			return null;
		}

		@Override
		public int delete(Uri uri, String selection, String[] selectionArgs) {
			return 0;
		}

		@Override
		public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
			return 0;
		}
	}

	public static class ListScreen extends FragmentActivity
			implements CollectionConfigListFragment.OnCollectionSelectedListener {
		CollectionConfigListFragment fragment;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			fragment = new CollectionConfigListFragment();
			getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commitNow();
		}

		@Override
		public void onAddLocalCalendarRequested() {
		}

		@Override
		public void onCollectionSelected(int collectionId, ContentValues collectionValues) {
		}

		@Override
		public void onAccountCreationRequested(int collectionId) {
		}
	}

	@Before
	public void setUp() {
		calendars.clear();
		Robolectric.buildContentProvider(RowsProvider.class).create(Servers.AUTHORITY);
		Robolectric.buildContentProvider(RowsProvider.class).create(DavCollections.AUTHORITY);
	}

	@Test
	public void aCalendarDeletedOnAnotherScreenIsGoneFromTheListOnComingBack() {
		calendars.add(7);
		calendars.add(8);
		ActivityController<ListScreen> screen = Robolectric.buildActivity(ListScreen.class).setup();
		CollectionConfigListFragment list = screen.get().fragment;
		assertNotNull(list.findPreference("7"));
		assertNotNull(list.findPreference("8"));

		// The screen for calendar 8 comes in front, and deletes it
		screen.pause();
		calendars.remove(Integer.valueOf(8));
		screen.resume();

		assertNotNull(list.findPreference("7"));
		assertNull("A deleted calendar must not stay in the list", list.findPreference("8"));
	}

	@Test
	public void aCalendarAddedOnAnotherScreenIsInTheListOnComingBack() {
		calendars.add(7);
		ActivityController<ListScreen> screen = Robolectric.buildActivity(ListScreen.class).setup();
		CollectionConfigListFragment list = screen.get().fragment;
		assertNull(list.findPreference("9"));

		screen.pause();
		calendars.add(9);
		screen.resume();

		assertNotNull(list.findPreference("9"));
	}
}
