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

package com.morphoss.acal.database.alarmmanager;

import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmRequest;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmResponse;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequest;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequestWithResponse;

/**
 * Interface for the AlarmQueueManager, providing abstraction for dependency injection.
 * Manages alarm scheduling and notifications for calendar events.
 */
public interface IAlarmQueueManager {

    /**
     * Add a listener to receive notifications of alarm changes.
     * @param listener The listener to add
     */
    void addListener(AlarmChangedListener listener);

    /**
     * Remove a previously registered listener.
     * @param listener The listener to remove
     */
    void removeListener(AlarmChangedListener listener);

    /**
     * Send an asynchronous request to the alarm manager.
     * @param request The request to process
     * @throws IllegalStateException if close() has been called
     */
    void sendRequest(AlarmRequest request) throws IllegalStateException;

    /**
     * Send a blocking request and wait for a response.
     * @param request The request to process
     * @param <E> The response type
     * @return The response from processing the request
     */
    <E> AlarmResponse<E> sendBlockingRequest(BlockingAlarmRequestWithResponse<E> request);

    /**
     * Send a blocking request and wait for completion.
     * @param request The request to process
     */
    void sendBlockingRequest(BlockingAlarmRequest request);

    /**
     * Close the alarm manager and release resources.
     * Must be called before termination.
     */
    void close();
}
