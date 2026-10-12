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
import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.DMAction;
import org.davical.acal.database.DMQueryBuilder;
import org.davical.acal.database.TableManager.QUERY_ACTION;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;

/**
 * The last step of committing a pending change, which is the same wherever
 * the collection is kept.
 */
final class CommittedChange {

	private static final String TAG = "aCal CommittedChange";

	private CommittedChange() {
	}

	/**
	 * Make the resource in the database what the change made it, and retire
	 * the pending change.
	 *
	 * @param isDelete true to remove the resource
	 * @param resourceData the columns of the resource to set, if it stays
	 */
	static void record(WriteableResourceTableManager processor, boolean isDelete, ContentValues resourceData,
				long resourceId, long pendingId) {
		DMQueryBuilder builder = processor.getNewQueryBuilder()
					.setAction(isDelete ? QUERY_ACTION.DELETE : QUERY_ACTION.UPDATE);
		builder.setValues(resourceData);
		builder.setWhereClause(ResourceTableManager.RESOURCE_ID + " = "+resourceId);

		DMAction action = builder.build();

		try {
			processor.syncToServer(action, resourceId, pendingId);
		}
		catch (Exception e) {
			Log.w(TAG, action.toString()+": Exception applying resource modification: " + e.getMessage());
			Log.println(Constants.LOGD,TAG, Log.getStackTraceString(e));
		}
	}
}
