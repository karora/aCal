/*
 * Copyright (C) 2011 Morphoss Ltd
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

package org.davical.acal.activity;

import java.util.Map;
import java.util.TreeMap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.davical.acal.Constants;
import org.davical.acal.R;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;
import org.davical.acal.storage.LocalCalendars;

public class CollectionConfigListFragment extends PreferenceFragmentCompat
		implements Preference.OnPreferenceClickListener {

	public static final String TAG = "CollectionConfigListFragment";

	private static final String KEY_ADD_LOCAL = "add_local_calendar";

	// Data from the Collection Table
	int collectionListCount = 0;
	private int[] collectionListIds;
	private Map<Integer, ContentValues> collectionData;
	private Map<Integer, ContentValues> serverData;
	private int serverListCount;
	int[] preferenceListIds;

	private OnCollectionSelectedListener listener;

	public interface OnCollectionSelectedListener {
		void onAddLocalCalendarRequested();
		void onCollectionSelected(int collectionId, ContentValues collectionValues);
		void onAccountCreationRequested(int collectionId);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (context instanceof OnCollectionSelectedListener) {
			listener = (OnCollectionSelectedListener) context;
		} else {
			throw new RuntimeException(context.toString() + " must implement OnCollectionSelectedListener");
		}
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		getCollectionListItems();
		createPreferenceHierarchy();
	}

	private void getCollectionListItems() {
		ContentResolver cr = requireContext().getContentResolver();

		// Get Servers Data
		Cursor mCursor = cr.query(Servers.CONTENT_URI, null, Servers.ACTIVE, null, Servers._ID);
		try {
			this.serverListCount = mCursor.getCount();
			this.serverData = new TreeMap<Integer, ContentValues>();
			mCursor.moveToFirst();
			while (!mCursor.isAfterLast()) {
				ContentValues cv = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(mCursor, cv);
				int serverId = cv.getAsInteger(Servers._ID);
				this.serverData.put(serverId, cv);
				mCursor.moveToNext();
			}
		} catch (Exception e) {
			Log.w(TAG, "Error getting server list", e);
		} finally {
			if (mCursor != null) mCursor.close();
		}

		// Get Collections Data
		mCursor = cr.query(DavCollections.CONTENT_URI, null, null, null,
				DavCollections.SERVER_ID + ",lower(" + DavCollections.DISPLAYNAME + ")");
		try {
			collectionListCount = mCursor.getCount();
			this.collectionListIds = new int[collectionListCount];
			this.collectionData = new TreeMap<Integer, ContentValues>();
			mCursor.moveToFirst();
			int i = 0;
			while (!mCursor.isAfterLast()) {
				ContentValues cv = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(mCursor, cv);
				int collectionId = cv.getAsInteger(DavCollections._ID);
				this.collectionListIds[i++] = collectionId;
				this.collectionData.put(collectionId, cv);
				mCursor.moveToNext();
			}
		} catch (Exception e) {
			Log.w(TAG, "Error getting collection list", e);
		} finally {
			if (mCursor != null) mCursor.close();
		}
	}

	private void createPreferenceHierarchy() {
		Context context = getPreferenceManager().getContext();
		PreferenceScreen preferenceRoot = getPreferenceManager().createPreferenceScreen(context);

		// Room for every collection and heading, and for the heading and the
		// row to add a calendar which what is kept on the phone always has.
		this.preferenceListIds = new int[collectionListCount + serverListCount + 2];
		PreferenceCategory currentCategory = null;
		PreferenceCategory localCategory = null;
		int lastServerId = -1;
		int prefRowId = 0;

		for (int i = 0; i < this.collectionListCount; i++) {
			int collectionId = collectionListIds[i];
			ContentValues cv = collectionData.get(collectionId);
			int serverId = cv.getAsInteger(DavCollections.SERVER_ID);
			if (serverData.get(serverId) == null || 1 != serverData.get(serverId).getAsInteger(Servers.ACTIVE))
				continue;
			boolean local = LocalCalendars.isLocalAccount(serverData.get(serverId));
			if (lastServerId != serverId) {
				currentCategory = new PreferenceCategory(context);
				currentCategory.setTitle(local ? getString(R.string.ThisPhone)
						: serverData.get(serverId).getAsString(Servers.FRIENDLY_NAME));
				currentCategory.setPersistent(false);
				preferenceRoot.addPreference(currentCategory);
				preferenceListIds[prefRowId++] = 0;
				lastServerId = serverId;
				if (local) localCategory = currentCategory;
			}
			String collectionColour = cv.getAsString(DavCollections.COLOUR);
			Preference thisPreference = new Preference(context);
			currentCategory.addPreference(thisPreference);
			thisPreference.setTitle(cv.getAsString(DavCollections.DISPLAYNAME));
			thisPreference.setSummary(local ? getString(R.string.KeptOnThisPhone)
					: cv.getAsString(DavCollections.COLLECTION_PATH));
			thisPreference.setPersistent(false);
			thisPreference.setKey(Integer.toString(collectionId));
			thisPreference.setOnPreferenceClickListener(this);
			preferenceListIds[prefRowId++] = collectionId;
			thisPreference.setEnabled(true);
			Log.println(Constants.LOGD, TAG, "Created preference for " + thisPreference.getTitle());
		}

		// What is kept on the phone always has its heading, with the way to
		// add to it.  Not when an addressbook is being chosen for an account:
		// the phone keeps no addressbooks.
		Intent activityIntent = requireActivity().getIntent();
		if (activityIntent == null || !CollectionConfigList.ACTION_CHOOSE_ADDRESSBOOK.equals(activityIntent.getAction())) {
			if (localCategory == null) {
				localCategory = new PreferenceCategory(context);
				localCategory.setTitle(R.string.ThisPhone);
				localCategory.setPersistent(false);
				preferenceRoot.addPreference(localCategory);
				preferenceListIds[prefRowId++] = 0;
			}
			Preference addLocal = new Preference(context);
			localCategory.addPreference(addLocal);
			addLocal.setTitle(R.string.AddLocalCalendar);
			addLocal.setSummary(R.string.AddLocalCalendarSummary);
			addLocal.setPersistent(false);
			addLocal.setKey(KEY_ADD_LOCAL);
			addLocal.setOnPreferenceClickListener(this);
			preferenceListIds[prefRowId++] = 0;
		}

		setPreferenceScreen(preferenceRoot);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (KEY_ADD_LOCAL.equals(preference.getKey())) {
			if (listener != null) listener.onAddLocalCalendarRequested();
			return true;
		}
		int collectionId = Integer.parseInt(preference.getKey());
		Intent activityIntent = requireActivity().getIntent();
		if (activityIntent != null && CollectionConfigList.ACTION_CHOOSE_ADDRESSBOOK.equals(activityIntent.getAction())) {
			if (listener != null) {
				listener.onAccountCreationRequested(collectionId);
			}
		} else {
			ContentValues toPass = collectionData.get(collectionId);
			if (listener != null) {
				listener.onCollectionSelected(collectionId, toPass);
			}
		}
		return true;
	}

	public void refresh() {
		getCollectionListItems();
		createPreferenceHierarchy();
	}

	public ContentValues getCollectionData(int collectionId) {
		return collectionData.get(collectionId);
	}

	public ContentValues getServerData(int serverId) {
		return serverData.get(serverId);
	}
}
