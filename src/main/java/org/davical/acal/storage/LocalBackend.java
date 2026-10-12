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
package org.davical.acal.storage;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.service.ServiceJob;
import org.davical.acal.storage.CommitResult.Outcome;

/**
 * Collections kept in aCal's own database and nowhere else.  A change is
 * committed by making the resource what the change says, and there is nothing
 * to fetch or to find.
 */
public class LocalBackend implements StorageBackend {

	@Override
	public boolean needsNetwork() {
		return false;
	}

	@Override
	public CommitResult commitPendingChange(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData, ContentValues pending) {

		String newData = pending.getAsString(ResourceTableManager.NEW_DATA);
		long resourceId = pending.getAsLong(ResourceTableManager.PEND_RESOURCE_ID);
		long pendingId = pending.getAsLong(ResourceTableManager.PENDING_ID);

		ContentValues resourceData = new ContentValues();
		if ( newData != null ) {
			resourceData.put(ResourceTableManager.RESOURCE_ID, resourceId);
			resourceData.put(ResourceTableManager.COLLECTION_ID, pending.getAsLong(ResourceTableManager.PEND_COLLECTION_ID));
			resourceData.put(ResourceTableManager.RESOURCE_NAME, nameFor(
						pending.getAsString(ResourceTableManager.RESOURCE_NAME),
						pending.getAsString(ResourceTableManager.UID), newData));
			resourceData.put(ResourceTableManager.RESOURCE_DATA, newData);
			resourceData.put(ResourceTableManager.ETAG, newEtag(System.currentTimeMillis()));
			resourceData.put(ResourceTableManager.NEEDS_SYNC, 0);
		}

		CommittedChange.record(processor, newData == null, resourceData, resourceId, pendingId);
		return new CommitResult(Outcome.APPLIED, 0, false);
	}

	/**
	 * @return the name the resource keeps if it has one, or one for it if not.
	 *         Every resource in a local collection has a name once committed,
	 *         as one in any other collection does.
	 */
	static String nameFor(String resourceName, String uid, String data) {
		return (ResourceNaming.hasName(resourceName) ? resourceName : ResourceNaming.newName(uid, data));
	}

	/**
	 * @return something that is different each time the resource changes,
	 *         which is all an etag has to be.
	 */
	static String newEtag(long now) {
		return "\"local-" + Long.toHexString(now) + "\"";
	}

	@Override
	public void commitCollectionProperties(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData) {
		// The collection row is the only place they are kept, and they are already in it
		collectionData.put(DavCollections.SYNC_METADATA, 0);
		processor.updateCollection(collectionData.getAsLong(DavCollections._ID), collectionData);
	}

	@Override
	public ServiceJob discoverCollections(int serverId) {
		return null;
	}

	@Override
	public ServiceJob updateCollections(int serverId) {
		return null;
	}

	@Override
	public ServiceJob refreshCollection(long collectionId, boolean now) {
		return null;
	}

	@Override
	public ServiceJob reloadCollection(long collectionId) {
		return null;
	}
}
