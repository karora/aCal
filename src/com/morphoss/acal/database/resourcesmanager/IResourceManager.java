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

package com.morphoss.acal.database.resourcesmanager;

import com.morphoss.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;
import com.morphoss.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequest;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ResourceRequest;

/**
 * Interface for the ResourceManager, providing abstraction for dependency injection.
 * Manages calendar resource data in the database and provides request-based access.
 */
public interface IResourceManager {

    /**
     * Add a listener to receive notifications of resource changes.
     * @param listener The listener to add
     */
    void addListener(ResourceChangedListener listener);

    /**
     * Remove a previously registered listener.
     * @param listener The listener to remove
     */
    void removeListener(ResourceChangedListener listener);

    /**
     * Send an asynchronous write request to the resource manager.
     * @param request The request to process
     */
    void sendRequest(ResourceRequest request);

    /**
     * Send a blocking write request and wait for completion.
     * @param request The request to process
     */
    void sendBlockingRequest(BlockingResourceRequest request);

    /**
     * Send a blocking write request and wait for a response.
     * @param request The request to process
     * @param <E> The response type
     * @return The response from processing the request
     */
    <E> ResourceResponse<E> sendBlockingRequest(BlockingResourceRequestWithResponse<E> request);

    /**
     * Send an asynchronous read request to the resource manager.
     * @param request The request to process
     */
    void sendRequest(ReadOnlyResourceRequest request);

    /**
     * Send a blocking read request and wait for a response.
     * @param request The request to process
     * @param <E> The response type
     * @return The response from processing the request
     */
    <E> ResourceResponse<E> sendBlockingRequest(ReadOnlyBlockingRequestWithResponse<E> request);

    /**
     * Close the resource manager and release resources.
     * Must be called before termination.
     */
    void close();
}
