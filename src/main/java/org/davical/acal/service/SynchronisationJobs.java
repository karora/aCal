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

package org.davical.acal.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;
import org.davical.acal.storage.StorageBackend;
import org.davical.acal.storage.StorageBackends;

public class SynchronisationJobs extends ServiceJob {

	public static final int		HOME_SET_DISCOVERY	= 0;
	public static final int		HOME_SETS_UPDATE	= 1;
	public static final int		CALENDAR_SYNC		= 2;

	public static final String	TAG					= "aCal SynchronisationJobs";

	public int					TIME_TO_EXECUTE		= 0;							// immediately
	private int					jobtype;
	private aCalService			context;

	public static enum WriteActions { UPDATE, INSERT, DELETE };

	
	public SynchronisationJobs( int jobtype ) {
		this.jobtype = jobtype;
	}

	@Override
	public void run( aCalService context ) {
		this.context = context;
		switch (jobtype) {
			case HOME_SET_DISCOVERY:
				refreshHomeSets();
				break;

			case HOME_SETS_UPDATE:
				refreshCollectionsFromHomeSets();
				break;
		}
	}

	public synchronized void refreshHomeSets() {
		for ( ContentValues serverData : activeServers() ) {
			StorageBackend backend = StorageBackends.forServerRow(serverData);
			if ( backend == null ) continue;
			ServiceJob sj = backend.discoverCollections(serverData.getAsInteger(Servers._ID));
			if ( sj != null ) context.addWorkerJob(sj);
		}
	}

	public synchronized void refreshCollectionsFromHomeSets() {
		for ( ContentValues serverData : activeServers() ) {
			StorageBackend backend = StorageBackends.forServerRow(serverData);
			if ( backend == null ) continue;
			ServiceJob sj = backend.updateCollections(serverData.getAsInteger(Servers._ID));
			if ( sj != null ) context.addWorkerJob(sj);
		}
	}

	private List<ContentValues> activeServers() {
		List<ContentValues> servers = new ArrayList<ContentValues>();
		Cursor mCursor = context.getContentResolver().query(Servers.CONTENT_URI, null, null, null, null);
		for ( mCursor.moveToFirst(); !mCursor.isAfterLast(); mCursor.moveToNext() ) {
			ContentValues serverData = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(mCursor, serverData);
			Integer active = serverData.getAsInteger(Servers.ACTIVE);
			if ( active != null && active == 1 ) servers.add(serverData);
		}
		mCursor.close();
		return servers;
	}

	/**
	 * Creates a sync job for ALL active collections.  If the collection was
	 * last synchronised less than 14 days ago we do a syncCollectionContents
	 * otherwise we do an initialCollectionSync.  We try and do these sync
	 * jobs with gaps between them.
	 *  
	 * @param worker
	 * @param context
	 */
	public static void startCollectionSync(IWorkerClass worker, Context context, long startInMillis) {
		ContentResolver cr = context.getContentResolver();
		ContentValues[] collectionsList = DavCollections.getCollections(cr, DavCollections.INCLUDE_ALL_ACTIVE);
		Map<Integer, StorageBackend> backends = new HashMap<Integer, StorageBackend>();
		String lastSyncString;
		AcalDateTime lastSync;
		int collectionId;

		for (ContentValues collectionValues : collectionsList) {
			Integer serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
			if ( serverId == null ) continue;
			if ( !backends.containsKey(serverId) ) backends.put(serverId, StorageBackends.forServer(serverId, cr));
			StorageBackend backend = backends.get(serverId);
			if ( backend == null ) continue;

			collectionId = collectionValues.getAsInteger(DavCollections._ID);
			lastSyncString = collectionValues.getAsString(DavCollections.LAST_SYNCHRONISED);
			if (lastSyncString != null) {
				lastSync = AcalDateTime.fromString(lastSyncString);
				if (lastSync.addDays(14).getMillis() > System.currentTimeMillis()) {
					// In this case we will schedule a normal sync on the collection
					// which will hopefully be *much* lighter weight.
					ServiceJob job = backend.refreshCollection(collectionId, false);
					if ( job == null ) continue;
					job.TIME_TO_EXECUTE = startInMillis;
					worker.addJobAndWake(job);
					startInMillis += 5000;
				}
			}
			else {
				ServiceJob job = backend.reloadCollection(collectionId);
				if ( job == null ) continue;
				job.TIME_TO_EXECUTE = startInMillis;
				worker.addJobAndWake(job);
				startInMillis += 15000;
			}
		}

	}
	
	
	// The following overrides are to prevent duplication of these jobs in the queue
	public boolean equals(Object o) {
		if (!(o instanceof SynchronisationJobs)) return false;
		if (((SynchronisationJobs) o).jobtype == this.jobtype) return true;
		return false;
	}

	public int hashCode() {
		return this.jobtype;
	}



	/**
	 * Returns a minimal Header[] array for a REPORT request.
	 * @param depth
	 * @return
	 */
	public static Header[] getReportHeaders( int depth ) {
		return new Header[] {
					new BasicHeader("Content-Type", "text/xml; charset=utf-8"),
					new BasicHeader("Prefer","return=minimal, depth-noroot"),  // draft-murchison-webdav-prefer-03
					new BasicHeader("Depth", Integer.toString(depth))
				};
	}

	
	
	@Override
	public String getDescription() {
		switch( jobtype ) {
			case HOME_SETS_UPDATE:
				return "Updating collections in all home sets";
			case HOME_SET_DISCOVERY:
				return "Discovering home sets for all servers";
		}
		Log.e(TAG,"No description defined for jobtype "+jobtype );
		return "Unknown SynchronisationJobs jobtype!";
	}

}
