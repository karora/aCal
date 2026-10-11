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

import java.io.IOException;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.service.ServiceJob;

/**
 * The things that differ according to where an account keeps its collections:
 * how a local change gets committed, how a collection is brought up to date
 * and how the collections of an account are found.
 *
 * The methods returning a ServiceJob return null when the backend has nothing
 * to do for that.
 */
public interface StorageBackend {

	/**
	 * @return true if committing changes needs a network connection, so there
	 *         is no point trying without one.
	 */
	public boolean needsNetwork();

	/**
	 * Commit one row of pending_change to wherever the collection is kept, and
	 * retire the row if that is the end of the matter.
	 *
	 * @param serverData the dav_server row of the account
	 * @param collectionData the dav_collection row of the collection
	 * @param pending the row, as returned by getPendingResources()
	 * @return true if the collection should be refreshed afterwards
	 */
	public boolean commitPendingChange(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData, ContentValues pending) throws IOException;

	/**
	 * Commit the name and colour of a collection which has sync_metadata set,
	 * clearing the flag once that is done.
	 */
	public void commitCollectionProperties(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData);

	/**
	 * @return a job to find where the collections of an account are and then
	 *         update the list of them.
	 */
	public ServiceJob discoverCollections(int serverId);

	/**
	 * @return a job to update the list of the collections of an account from
	 *         the places they were last found in.
	 */
	public ServiceJob updateCollections(int serverId);

	/**
	 * @param now true to do it at once, false to leave it to the normal schedule
	 * @return a job to bring the contents of a collection up to date.
	 */
	public ServiceJob refreshCollection(long collectionId, boolean now);

	/**
	 * @return a job to fetch the contents of a collection from scratch.
	 */
	public ServiceJob reloadCollection(long collectionId);
}
