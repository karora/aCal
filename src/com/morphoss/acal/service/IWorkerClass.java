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

package com.morphoss.acal.service;

import java.util.Collection;

/**
 * Interface for the WorkerClass, providing abstraction for dependency injection.
 * Manages background job execution for the aCal service.
 */
public interface IWorkerClass {

    /**
     * Add a job to the queue and wake the worker thread.
     * @param job The job to add
     */
    void addJobAndWake(ServiceJob job);

    /**
     * Add multiple jobs to the queue and wake the worker thread.
     * @param jobs The collection of jobs to add
     */
    void addJobsAndWake(Collection<ServiceJob> jobs);

    /**
     * Add multiple jobs to the queue and wake the worker thread.
     * @param jobs The array of jobs to add
     */
    void addJobsAndWake(ServiceJob[] jobs);

    /**
     * Reset the worker thread.
     */
    void resetWorker();

    /**
     * Kill the worker thread.
     */
    void killWorker();

    /**
     * Check if there is work waiting to be executed.
     * @return true if work is waiting
     */
    boolean workWaiting();

    /**
     * Get the time of the last action.
     * @return timestamp of last action
     */
    long getTimeOfLastAction();

    /**
     * Get the time of the next scheduled action.
     * @return timestamp of next action
     */
    long getTimeOfNextAction();
}
