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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;
import org.davical.acal.storage.IcsFile;

/**
 * Brings events, tasks and notes into a collection.  One that is already
 * there, going by its UID, is replaced.  Each goes in as a pending change, so
 * it is committed the same way as if the user had made it by hand.
 */
public class RRImportItems extends BlockingResourceRequestWithResponse<RRImportItems.Counts> {

	/** How many of what was given were new, replaced something, or were there already unchanged. */
	public static class Counts {
		public int added = 0;
		public int replaced = 0;
		public int unchanged = 0;
	}

	private final long collectionId;
	private final List<IcsFile.Item> items;

	public RRImportItems(long collectionId, List<IcsFile.Item> items) {
		this.collectionId = collectionId;
		this.items = items;
	}

	private static String keyFor(String type, String uid) {
		return type + "\n" + uid;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		final Counts counts = new Counts();

		// What is in the collection already, by what it is
		Map<Long, String> current = RRCollectionData.currentData(processor, collectionId);
		Map<String, Long> existing = new HashMap<String, Long>();
		for ( Map.Entry<Long, String> resource : current.entrySet() ) {
			if ( resource.getValue() == null ) continue;
			for ( IcsFile.Item held : IcsFile.split(resource.getValue()) ) {
				existing.put(keyFor(held.type, held.uid), resource.getKey());
			}
		}

		for ( IcsFile.Item item : items ) {
			Long resourceId = existing.get(keyFor(item.type, item.uid));
			if ( resourceId == null ) {
				long newId = processor.addPending(collectionId, -1, null, item.data, item.uid);
				// The file may hold the same thing twice
				existing.put(keyFor(item.type, item.uid), newId);
				current.put(newId, item.data);
				counts.added++;
			}
			else if ( item.data.equals(current.get(resourceId)) ) {
				counts.unchanged++;
			}
			else {
				processor.addPending(collectionId, resourceId, current.get(resourceId), item.data, item.uid);
				current.put(resourceId, item.data);
				counts.replaced++;
			}
		}

		this.postResponse(new ResourceResponse<Counts>() {
			@Override
			public Counts result() {
				return counts;
			}
		});
	}
}
