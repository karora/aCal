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

package com.morphoss.acal.database.cachemanager;

/**
 * Interface for the CacheManager, providing abstraction for dependency injection.
 * Manages the event cache for efficient calendar data access.
 */
public interface ICacheManager {

    /**
     * Add a listener to receive notifications of cache changes.
     * @param listener The listener to add
     */
    void addListener(CacheChangedListener listener);

    /**
     * Remove a previously registered listener.
     * @param listener The listener to remove
     */
    void removeListener(CacheChangedListener listener);

    /**
     * Send an asynchronous request to the cache manager.
     * @param request The request to process
     * @throws IllegalStateException if close() has been called
     */
    void sendRequest(CacheRequest request) throws IllegalStateException;

    /**
     * Send a blocking request and wait for a response.
     * @param request The request to process
     * @param <E> The response type
     * @return The response from processing the request
     * @throws IllegalStateException if close() has been called
     */
    <E> CacheResponse<E> sendRequest(BlockingCacheRequestWithResponse<E> request) throws IllegalStateException;

    /**
     * Close the cache manager and release resources.
     * Must be called before termination.
     */
    void close();
}
