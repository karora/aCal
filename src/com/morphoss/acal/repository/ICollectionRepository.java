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

package com.morphoss.acal.repository;

import java.util.List;

import android.content.ContentValues;

/**
 * Repository interface for accessing calendar collections.
 * Provides abstraction over the DavCollections provider.
 */
public interface ICollectionRepository {

    /**
     * Get all active collections.
     *
     * @return List of collection ContentValues
     */
    List<ContentValues> getActiveCollections();

    /**
     * Get all collections for a specific server.
     *
     * @param serverId The server ID
     * @return List of collection ContentValues
     */
    List<ContentValues> getCollectionsByServer(long serverId);

    /**
     * Get a specific collection by ID.
     *
     * @param collectionId The collection ID
     * @return The collection ContentValues, or null if not found
     */
    ContentValues getCollectionById(long collectionId);

    /**
     * Get collections that support events.
     *
     * @return List of event-capable collections
     */
    List<ContentValues> getEventCollections();

    /**
     * Get collections that support tasks.
     *
     * @return List of task-capable collections
     */
    List<ContentValues> getTaskCollections();

    /**
     * Get collections that support notes/journals.
     *
     * @return List of journal-capable collections
     */
    List<ContentValues> getNoteCollections();

    /**
     * Get the colour for a collection.
     *
     * @param collectionId The collection ID
     * @return The colour as an integer, or default if not set
     */
    int getCollectionColour(long collectionId);

    /**
     * Get the display name for a collection.
     *
     * @param collectionId The collection ID
     * @return The display name
     */
    String getCollectionName(long collectionId);

    /**
     * Check if a collection is writable.
     *
     * @param collectionId The collection ID
     * @return true if writable
     */
    boolean isCollectionWritable(long collectionId);
}
