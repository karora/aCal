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

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;

/**
 * Fetches the pending changes that were refused and are waiting for the user
 * to say what should be done with them.
 */
public class RRRefusedChanges extends BlockingResourceRequestWithResponse<ArrayList<ContentValues>> {

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		final ArrayList<ContentValues> refused = new ArrayList<ContentValues>();
		for ( ContentValues pending : processor.getPendingResources() ) {
			Integer state = pending.getAsInteger(ResourceTableManager.PEND_STATE);
			if ( state != null && state == ResourceTableManager.PENDING_REFUSED ) refused.add(pending);
		}
		this.postResponse(new ResourceResponse<ArrayList<ContentValues>>() {
			@Override
			public ArrayList<ContentValues> result() {
				return refused;
			}
		});
	}
}
