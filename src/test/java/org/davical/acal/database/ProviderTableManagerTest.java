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

package org.davical.acal.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.davical.acal.database.TableManager.QUERY_ACTION;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * A table reached through a content provider, as the cache of events is.
 */
@RunWith(RobolectricTestRunner.class)
public class ProviderTableManagerTest {

	private static final String AUTHORITY = "org.davical.acal.test.rows";
	private static final Uri ROWS = Uri.parse("content://" + AUTHORITY);

	/** Keeps rows of one "name" column, and notes when a transaction is open. */
	public static class RowsProvider extends ContentProvider {
		static final List<String> rows = new ArrayList<String>();
		static boolean inTransaction = false;
		static boolean failInserts = false;
		static boolean hideRowsFromQueries = false;
		private static List<String> rowsBefore;
		private static boolean approved;

		@Override
		public boolean onCreate() {
			return true;
		}

		@Override
		public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
			MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "name" });
			if ( hideRowsFromQueries ) return cursor;
			for ( int i = 0; i < rows.size(); i++ ) cursor.addRow(new Object[] { i + 1, rows.get(i) });
			return cursor;
		}

		@Override
		public Uri insert(Uri uri, ContentValues values) {
			if ( failInserts ) throw new IllegalStateException("The table is broken");
			rows.add(values.getAsString("name"));
			return ContentUris.withAppendedId(uri, rows.size());
		}

		@Override
		public int delete(Uri uri, String selection, String[] selectionArgs) {
			int count = rows.size();
			rows.clear();
			return count;
		}

		@Override
		public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
			String step = uri.getLastPathSegment();
			if ( "begin".equals(step) ) {
				inTransaction = true;
				approved = false;
				rowsBefore = new ArrayList<String>(rows);
			}
			if ( "approve".equals(step) ) approved = true;
			if ( "commit".equals(step) ) {
				inTransaction = false;
				if ( !approved ) {
					// Rolled back
					rows.clear();
					rows.addAll(rowsBefore);
				}
			}
			return 1;
		}

		@Override
		public String getType(Uri uri) {
			return null;
		}
	}

	/** Notes what its listeners would have been told, and whether the changes were there to read by then. */
	private static class Table extends ProviderTableManager {
		final List<ArrayList<DataChangeEvent>> told = new ArrayList<ArrayList<DataChangeEvent>>();
		boolean toldInsideTransaction = false;

		Table() {
			super(RuntimeEnvironment.getApplication());
		}

		@Override
		protected Uri getCallUri() {
			return ROWS;
		}

		@Override
		protected Uri getQueryUri() {
			return ROWS;
		}

		@Override
		public void dataChanged(ArrayList<DataChangeEvent> changes) {
			told.add(changes);
			if ( RowsProvider.inTransaction ) toldInsideTransaction = true;
		}
	}

	private Table table;

	@Before
	public void setUp() {
		RowsProvider.rows.clear();
		RowsProvider.inTransaction = false;
		RowsProvider.failInserts = false;
		RowsProvider.hideRowsFromQueries = false;
		Robolectric.buildContentProvider(RowsProvider.class).create(AUTHORITY);
		table = new Table();
	}

	private static DMQueryList inserting(String... names) {
		DMQueryList queries = new DMQueryList();
		for ( String name : names ) {
			ContentValues row = new ContentValues();
			row.put("name", name);
			queries.addAction(new DMInsertQuery(null, row));
		}
		return queries;
	}

	/**
	 * The changes were collected and then dropped, so nothing listening to
	 * the cache ever heard of one: a new task, note or event was not in its
	 * list until the list was made again for some other reason.
	 */
	@Test
	public void listenersAreToldWhatASetOfActionsChanged() {
		assertTrue(table.processActions(inserting("first", "second")));

		assertEquals(1, table.told.size());
		ArrayList<DataChangeEvent> changes = table.told.get(0);
		assertEquals(2, changes.size());
		assertEquals(QUERY_ACTION.INSERT, changes.get(0).action);
		assertEquals("first", changes.get(0).getData().getAsString("name"));
		assertEquals("second", changes.get(1).getData().getAsString("name"));
	}

	/** A listener goes and reads the table, so the changes have to be in it by then. */
	@Test
	public void listenersAreToldOnlyOnceTheChangesAreCommitted() {
		table.processActions(inserting("first"));

		assertFalse(table.toldInsideTransaction);
		assertFalse(RowsProvider.inTransaction);
	}

	@Test
	public void deletingTellsListenersWhatWasThere() {
		table.processActions(inserting("first", "second"));
		table.told.clear();

		DMQueryList queries = new DMQueryList();
		queries.addAction(new DMDeleteQuery(null, null));
		assertTrue(table.processActions(queries));

		assertEquals(1, table.told.size());
		assertEquals(2, table.told.get(0).size());
		assertEquals(QUERY_ACTION.DELETE, table.told.get(0).get(0).action);
		assertEquals("first", table.told.get(0).get(0).getData().getAsString("name"));
	}

	/**
	 * What is cached is read through a join to its collection, so the rows of
	 * a collection that has just been deleted cannot be read, though they can
	 * be deleted.  With nothing read there was nothing to tell of, and that
	 * was taken to mean nothing had been done: the delete was rolled back,
	 * and the events of a deleted calendar stayed in the cache.
	 */
	@Test
	public void aDeleteIsKeptThoughTheRowsItRemovedCouldNotBeRead() {
		table.processActions(inserting("first", "second"));
		RowsProvider.hideRowsFromQueries = true;

		DMQueryList queries = new DMQueryList();
		queries.addAction(new DMDeleteQuery(null, null));
		table.processActions(queries);

		assertEquals(0, RowsProvider.rows.size());
	}

	@Test
	public void whatWasDoneBeforeAFailureIsUndone() {
		table.processActions(inserting("kept"));
		RowsProvider.failInserts = true;

		DMQueryList queries = new DMQueryList();
		queries.addAction(new DMDeleteQuery(null, null));
		ContentValues row = new ContentValues();
		row.put("name", "fails");
		queries.addAction(new DMInsertQuery(null, row));
		assertFalse(table.processActions(queries));

		assertEquals(1, RowsProvider.rows.size());
		assertEquals("kept", RowsProvider.rows.get(0));
	}

	@Test
	public void nothingIsToldWhenNothingChanged() {
		DMQueryList queries = new DMQueryList();
		queries.addAction(new DMDeleteQuery(null, null));

		assertFalse(table.processActions(queries));
		assertEquals(0, table.told.size());
	}

	@Test
	public void nothingIsToldWhenTheActionsFailed() {
		RowsProvider.failInserts = true;

		assertFalse(table.processActions(inserting("first")));
		assertEquals(0, table.told.size());
		assertFalse(RowsProvider.inTransaction);
	}

	/** Whoever opened the transaction has not committed yet, and will do the telling. */
	@Test
	public void nothingIsToldFromInsideSomeoneElsesTransaction() {
		table.beginTx();
		assertTrue(table.processActions(inserting("first")));

		assertEquals(0, table.told.size());
		table.endTx();
	}
}
