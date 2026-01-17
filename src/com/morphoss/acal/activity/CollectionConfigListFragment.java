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

package com.morphoss.acal.activity;

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

import com.morphoss.acal.Constants;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.Servers;

public class CollectionConfigListFragment extends PreferenceFragmentCompat
		implements Preference.OnPreferenceClickListener {

	public static final String TAG = "CollectionConfigListFragment";

	// Data from the Collection Table
	int collectionListCount = 0;
	private int[] collectionListIds;
	private Map<Integer, ContentValues> collectionData;
	private Map<Integer, ContentValues> serverData;
	private int serverListCount;
	int[] preferenceListIds;

	private OnCollectionSelectedListener listener;

	public interface OnCollectionSelectedListener {
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

		this.preferenceListIds = new int[collectionListCount + serverListCount];
		PreferenceCategory currentCategory = null;
		int lastServerId = -1;
		int prefRowId = 0;

		for (int i = 0; i < this.collectionListCount; i++) {
			int collectionId = collectionListIds[i];
			ContentValues cv = collectionData.get(collectionId);
			int serverId = cv.getAsInteger(DavCollections.SERVER_ID);
			if (serverData.get(serverId) == null || 1 != serverData.get(serverId).getAsInteger(Servers.ACTIVE))
				continue;
			if (lastServerId != serverId) {
				currentCategory = new PreferenceCategory(context);
				currentCategory.setTitle(serverData.get(serverId).getAsString(Servers.FRIENDLY_NAME));
				currentCategory.setPersistent(false);
				preferenceRoot.addPreference(currentCategory);
				preferenceListIds[prefRowId++] = 0;
				lastServerId = serverId;
			}
			String collectionColour = cv.getAsString(DavCollections.COLOUR);
			Preference thisPreference = new Preference(context);
			currentCategory.addPreference(thisPreference);
			thisPreference.setTitle(cv.getAsString(DavCollections.DISPLAYNAME));
			thisPreference.setSummary(cv.getAsString(DavCollections.COLLECTION_PATH));
			thisPreference.setPersistent(false);
			thisPreference.setKey(Integer.toString(collectionId));
			thisPreference.setOnPreferenceClickListener(this);
			preferenceListIds[prefRowId++] = collectionId;
			thisPreference.setEnabled(true);
			Log.println(Constants.LOGD, TAG, "Created preference for " + thisPreference.getTitle());
		}

		setPreferenceScreen(preferenceRoot);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
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
