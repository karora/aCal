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
package org.davical.acal.database.resourcesmanager.requests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;

/**
 * Fetches the data of every resource in a collection as the user now sees it:
 * with the changes that are still waiting to be committed, and without what
 * is waiting to be deleted.
 */
public class RRCollectionData extends BlockingResourceRequestWithResponse<ArrayList<String>> {

	private final long collectionId;

	public RRCollectionData(long collectionId) {
		this.collectionId = collectionId;
	}

	/**
	 * @return the data of each resource of the collection by its id, as it
	 *         will be once the changes waiting for it are committed.  A
	 *         resource waiting to be deleted maps to null.
	 */
	static Map<Long, String> currentData(WriteableResourceTableManager processor, long collectionId) {
		Map<Long, String> data = new HashMap<Long, String>();
		for ( ContentValues resource : processor.query(null,
					ResourceTableManager.COLLECTION_ID+" = ?", new String[] { Long.toString(collectionId) },
					ResourceTableManager.RESOURCE_ID) ) {
			data.put(resource.getAsLong(ResourceTableManager.RESOURCE_ID),
					resource.getAsString(ResourceTableManager.RESOURCE_DATA));
		}
		for ( ContentValues pending : processor.getPendingResources() ) {
			Long pendingCollection = pending.getAsLong(ResourceTableManager.PEND_COLLECTION_ID);
			if ( pendingCollection == null || pendingCollection != collectionId ) continue;
			data.put(pending.getAsLong(ResourceTableManager.PEND_RESOURCE_ID),
					pending.getAsString(ResourceTableManager.NEW_DATA));
		}
		return data;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		final ArrayList<String> result = new ArrayList<String>();
		for ( String data : currentData(processor, collectionId).values() ) {
			if ( data != null ) result.add(data);
		}
		this.postResponse(new ResourceResponse<ArrayList<String>>() {
			@Override
			public ArrayList<String> result() {
				return result;
			}
		});
	}
}
