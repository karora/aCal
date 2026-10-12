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

package org.davical.acal.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.database.Cursor;
import android.net.Uri;

/**
 * Which collections a query is for.  Nothing here opens the database.
 */
@RunWith(RobolectricTestRunner.class)
public class DavCollectionsTest {

	private static Uri uri(String path) {
		return Uri.parse("content://" + DavCollections.AUTHORITY + path);
	}

	@Test
	public void aQueryCanBeForAllOfThemOrOneOrThoseOfAServer() {
		assertTrue(DavCollections.isQueryable(DavCollections.CONTENT_URI));
		assertTrue(DavCollections.isQueryable(uri("/37")));
		assertTrue(DavCollections.isQueryable(uri("/server/2")));
	}

	/**
	 * An id of -1 means "none".  It was not recognised as an id, so the query
	 * had no condition and gave every collection, and the first of them was
	 * taken to be the one asked for: a new note went into the first calendar
	 * whichever was chosen as the default.
	 */
	@Test
	public void aQueryForNoCollectionGivesNothing() {
		assertFalse(DavCollections.isQueryable(uri("/-1")));
		assertFalse(DavCollections.isQueryable(uri("/abc")));
		assertFalse(DavCollections.isQueryable(uri("/37/more")));

		Cursor c = new DavCollections().query(uri("/-1"), null, null, null, null);
		try {
			assertEquals(0, c.getCount());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void thereIsNoRowForAnIdBelowZero() {
		assertNull(DavCollections.getRow(-1, RuntimeEnvironment.getApplication().getContentResolver()));
	}
}
