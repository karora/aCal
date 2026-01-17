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

/**
 * Monitors memory usage and determines when the service should restart.
 * Extracted from aCalService to promote single responsibility principle.
 *
 * This class checks heap memory usage and signals when the service
 * should restart to prevent OutOfMemoryErrors.
 */
public class MemoryMonitor {

    /**
     * Threshold percentage for heap usage that triggers a restart.
     * When total memory exceeds this percentage of max memory, service should restart.
     */
    private static final int HEAP_THRESHOLD_PERCENT = 115;

    /**
     * Default delay in seconds before restarting the service.
     */
    private static final long DEFAULT_RESTART_DELAY_SECONDS = 30;

    private final Runtime runtime;

    /**
     * Creates a new MemoryMonitor using the default Runtime.
     */
    public MemoryMonitor() {
        this(Runtime.getRuntime());
    }

    /**
     * Creates a new MemoryMonitor with a custom Runtime.
     * Useful for testing.
     *
     * @param runtime The Runtime to use for memory checks
     */
    MemoryMonitor(Runtime runtime) {
        this.runtime = runtime;
    }

    /**
     * Check if the service should restart due to high memory usage.
     *
     * @return true if heap usage exceeds threshold and service should restart
     */
    public boolean shouldRestartService() {
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();

        // Calculate percentage of max memory being used
        long usagePercent = (totalMemory * 100) / maxMemory;

        return usagePercent > HEAP_THRESHOLD_PERCENT;
    }

    /**
     * Get the recommended delay before restarting the service.
     *
     * @return delay in seconds
     */
    public long getRestartDelaySeconds() {
        return DEFAULT_RESTART_DELAY_SECONDS;
    }

    /**
     * Get the current heap usage as a percentage of max memory.
     *
     * @return percentage of max memory being used
     */
    public long getHeapUsagePercent() {
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        return (totalMemory * 100) / maxMemory;
    }

    /**
     * Get free memory available in the heap.
     *
     * @return free memory in bytes
     */
    public long getFreeMemory() {
        return runtime.freeMemory();
    }

    /**
     * Get total memory currently allocated.
     *
     * @return total memory in bytes
     */
    public long getTotalMemory() {
        return runtime.totalMemory();
    }

    /**
     * Get maximum memory that can be allocated.
     *
     * @return max memory in bytes
     */
    public long getMaxMemory() {
        return runtime.maxMemory();
    }
}
