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
 * Repository interface for accessing CalDAV/CardDAV servers.
 * Provides abstraction over the Servers provider.
 */
public interface IServerRepository {

    /**
     * Get all configured servers.
     *
     * @return List of server ContentValues
     */
    List<ContentValues> getAllServers();

    /**
     * Get all active servers.
     *
     * @return List of active server ContentValues
     */
    List<ContentValues> getActiveServers();

    /**
     * Get a specific server by ID.
     *
     * @param serverId The server ID
     * @return The server ContentValues, or null if not found
     */
    ContentValues getServerById(long serverId);

    /**
     * Get a server by friendly name.
     *
     * @param name The server name
     * @return The server ContentValues, or null if not found
     */
    ContentValues getServerByName(String name);

    /**
     * Check if a server is active.
     *
     * @param serverId The server ID
     * @return true if active
     */
    boolean isServerActive(long serverId);

    /**
     * Get the display name for a server.
     *
     * @param serverId The server ID
     * @return The display name
     */
    String getServerName(long serverId);

    /**
     * Add a new server.
     *
     * @param server The server configuration
     * @return The new server ID
     */
    long addServer(ContentValues server);

    /**
     * Update an existing server.
     *
     * @param serverId The server ID
     * @param server The updated configuration
     * @return true if updated
     */
    boolean updateServer(long serverId, ContentValues server);

    /**
     * Delete a server and its collections.
     *
     * @param serverId The server ID
     * @return true if deleted
     */
    boolean deleteServer(long serverId);
}
