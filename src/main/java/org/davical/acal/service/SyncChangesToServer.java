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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.storage.BackendType;
import org.davical.acal.storage.CommitResult;
import org.davical.acal.storage.RetryPolicy;
import org.davical.acal.storage.StorageBackend;
import org.davical.acal.storage.StorageBackends;

/**
 * Commits the local changes waiting in pending_change, and the collections
 * with changed properties, through the storage backend of the account each
 * belongs to.
 */
public class SyncChangesToServer extends ServiceJob implements BlockingResourceRequest {

	public static final String	TAG					= "aCal SyncChangesToServer";

	private static boolean DEBUG = true && Constants.DEBUG_MODE;

	private final boolean tryEverythingNow;
	private aCalService acalService;

	// One backend of each type for the whole run, so a backend can keep hold
	// of things like its connection between one change and the next.
	private Map<BackendType, StorageBackend> backends;

	private Map<Long, StorageBackend> collectionsToRefresh = null;

	private boolean online;

	// When to run again, or 0 if nothing is left that running again would help
	private long runAgainAt = 0;

	private WriteableResourceTableManager processor;

    private ContentResolver cr;


	public SyncChangesToServer() {
		this(false);
	}

	/**
	 * @param tryEverythingNow true to try the changes which are waiting their
	 *        turn to be tried again as well, as when the user asks for a sync.
	 */
	public SyncChangesToServer(boolean tryEverythingNow) {
		this.tryEverythingNow = tryEverythingNow;
		this.TIME_TO_EXECUTE = System.currentTimeMillis();
	}

	@Override
	public void run(aCalService context) {
		this.acalService = context;
		ResourceManager rm = ResourceManager.getInstance(context);
		//send request
		rm.sendBlockingRequest(this);

		if ( runAgainAt > 0 ) {
			// Whatever is still waiting then is waiting its turn like the rest
			ServiceJob again = new SyncChangesToServer();
			again.TIME_TO_EXECUTE = runAgainAt;
			context.addWorkerJob(again);
		}

	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		this.processor = processor;
		this.cr = acalService.getContentResolver();
		this.backends = new EnumMap<BackendType, StorageBackend>(BackendType.class);

		ArrayList<ContentValues> pendingChangesList = processor.getPendingResources();
		ArrayList<ContentValues> syncCollectionMetadataList = syncCollectionList();

		if ( pendingChangesList.isEmpty() && syncCollectionMetadataList.isEmpty() ) {
			if (DEBUG) Log.println(Constants.LOGD, TAG, "No local changes to synchronise.");
			setProcessed();
			return; // without rescheduling
		}

		online = connectivityAvailable();
		if ( online )
			Log.println(Constants.LOGI,TAG, "Starting sync of "+pendingChangesList.size()+" local changes");
		else if (DEBUG)
			Log.println(Constants.LOGD,TAG, "No connectivity available to sync local changes.");

		collectionsToRefresh = new HashMap<Long, StorageBackend>();
		runAgainAt = 0;

		try {
		    for( ContentValues collectionData : syncCollectionMetadataList ) {
		        commitCollectionProperties(collectionData);
		        runAgainBy(System.currentTimeMillis() + RetryPolicy.FIRST_WAIT);
		    }
			for( ContentValues pendingChange :  pendingChangesList )
				commitOneChange(pendingChange);

			for( Map.Entry<Long, StorageBackend> toRefresh : collectionsToRefresh.entrySet() ) {
				ServiceJob job = toRefresh.getValue().refreshCollection(toRefresh.getKey(), true);
				if ( job != null ) acalService.addWorkerJob(job);
			}
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
			runAgainBy(System.currentTimeMillis() + RetryPolicy.FIRST_WAIT);
		}

		setProcessed();
	}


	/**
	 * Something is still waiting which can next be tried at this time.
	 */
	private void runAgainBy(long when) {
		// Not sooner than the shortest wait, however many changes are due
		when = Math.max(when, System.currentTimeMillis() + RetryPolicy.FIRST_WAIT);
		if ( runAgainAt == 0 || when < runAgainAt ) runAgainAt = when;
	}


	private boolean connectivityAvailable() {
		try {
			ConnectivityManager conMan = (ConnectivityManager) processor.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = conMan.getActiveNetworkInfo();
			if ( netInfo.isConnected() ) return true;
		}
		catch ( Exception e ) {
		}
		return false;
	}


    /**
     * Get the current set of collections that need metadata synced
     */
    private ArrayList<ContentValues> syncCollectionList() {
        ArrayList<ContentValues> currentPathList = new ArrayList<ContentValues>();
        Cursor cursor = cr.query(DavCollections.CONTENT_URI, null, DavCollections.SYNC_METADATA+"=1", null, null);
        for( cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext() ) {
            ContentValues cv = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, cv);
            currentPathList.add(cv);
        }
        cursor.close();
        return currentPathList;
    }


	/**
	 * @return the backend to commit through for this account right now, or
	 *         null to leave its changes waiting: when the account is of a type
	 *         we do not know, or needs a network we do not have.
	 */
	private StorageBackend backendToCommitWith(ContentValues serverData) {
		BackendType type = BackendType.fromServerRow(serverData);
		if ( type == null ) {
			Log.w(TAG, "Leaving changes for an account with an unknown storage backend.");
			return null;
		}
		StorageBackend backend = backends.get(type);
		if ( backend == null ) {
			backend = StorageBackends.forType(type);
			backends.put(type, backend);
		}
		if ( backend.needsNetwork() && !online ) return null;
		return backend;
	}


	private void commitOneChange(ContentValues pending) throws IOException {

		long pendingId = pending.getAsLong(ResourceTableManager.PENDING_ID);
		Integer state = pending.getAsInteger(ResourceTableManager.PEND_STATE);
		if ( state != null && state == ResourceTableManager.PENDING_REFUSED ) {
			// Not until the user says so
			return;
		}

		Integer attempts = pending.getAsInteger(ResourceTableManager.PEND_ATTEMPTS);
		if ( attempts == null ) attempts = 0;
		Long lastTried = pending.getAsLong(ResourceTableManager.PEND_LAST_TRIED);
		long now = System.currentTimeMillis();
		if ( !tryEverythingNow && !RetryPolicy.isDue(attempts, lastTried, now) ) {
			runAgainBy(RetryPolicy.dueAt(attempts, lastTried));
			return;
		}

		long collectionId = pending.getAsLong(ResourceTableManager.PEND_COLLECTION_ID);
		ContentValues collectionData = processor.getCollectionRow(collectionId);
		if (collectionData == null) {
			invalidPendingChange(pending.getAsInteger(ResourceTableManager.PENDING_ID),
						"Error getting collection data from DB - deleting invalid pending change record." );
			return;
		}

		int serverId = collectionData.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverData = processor.getServerRow(serverId);
		if (serverData == null) {
			invalidPendingChange(pending.getAsInteger(ResourceTableManager.PENDING_ID),
						"Error getting server data from DB - deleting invalid pending change record." );
			Log.e(TAG, "Deleting invalid collection Record.");
			processor.deleteInvalidCollectionRecord(collectionId);
			return;
		}

		StorageBackend backend = backendToCommitWith(serverData);
		if ( backend == null ) {
			runAgainBy(now + RetryPolicy.FIRST_WAIT);
			return;
		}

		CommitResult result = backend.commitPendingChange(processor, serverData, collectionData, pending);
		if ( result.refreshCollection ) collectionsToRefresh.put(collectionId, backend);

		switch ( result.outcome ) {
			case APPLIED:
				break;

			case RETRY:
				processor.recordPendingAttempt(pendingId, false, result.status);
				runAgainBy(RetryPolicy.dueAt(attempts + 1, System.currentTimeMillis()));
				break;

			case REFUSED:
				processor.recordPendingAttempt(pendingId, true, result.status);
				break;
		}
	}


	private void commitCollectionProperties(ContentValues collectionData) {
		ContentValues serverData = processor.getServerRow(collectionData.getAsInteger(DavCollections.SERVER_ID));
		if ( serverData == null ) {
			Log.e(TAG,"No server for collection "+collectionData.getAsString(DavCollections.COLLECTION_PATH)
						+" to send its properties to.");
			return;
		}
		StorageBackend backend = backendToCommitWith(serverData);
		if ( backend != null ) backend.commitCollectionProperties(processor, serverData, collectionData);
	}


	private void invalidPendingChange(long pendingId, String msg) {
		Log.e(TAG, msg );
		processor.deletePendingChange(pendingId);
	}


	@Override
	public String getDescription() {
		return "Syncing local changes back to the server.";
	}

	private boolean processingComplete = false;

	@Override
	public boolean isProcessed() { return this.processingComplete; }
	@Override
	public synchronized void setProcessed() { this.processingComplete = true; }

}
