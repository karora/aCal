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

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.morphoss.acal.Constants;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for scheduling sync work using WorkManager.
 * Replaces AlarmManager-based scheduling with WorkManager's more reliable
 * background execution that respects battery optimization.
 */
public class SyncWorkScheduler {

    public static final String TAG = "SyncWorkScheduler";

    // Default periodic interval: 2 hours (same as previous AlarmManager interval)
    private static final long PERIODIC_INTERVAL_HOURS = 2;

    // Minimum interval for WorkManager periodic work is 15 minutes
    private static final long MIN_PERIODIC_INTERVAL_MINUTES = 15;

    /**
     * Schedule periodic sync work. This replaces the AlarmManager-based
     * service restart scheduling.
     *
     * @param context Application context
     */
    public static void schedulePeriodicSync(Context context) {
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Scheduling periodic sync work");
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putString(SyncWorker.KEY_SYNC_TYPE, SyncWorker.SYNC_TYPE_PERIODIC)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest);

        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Periodic sync work scheduled for every " + PERIODIC_INTERVAL_HOURS + " hours");
        }
    }

    /**
     * Schedule an immediate one-time sync, typically used when the UI starts.
     *
     * @param context Application context
     */
    public static void scheduleImmediateSync(Context context) {
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Scheduling immediate sync work");
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putString(SyncWorker.KEY_SYNC_TYPE, SyncWorker.SYNC_TYPE_UI_STARTED)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                SyncWorker.WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                syncRequest);
    }

    /**
     * Schedule a delayed sync. This replaces the AlarmManager-based delayed
     * service restart.
     *
     * @param context      Application context
     * @param delaySeconds Delay in seconds before sync should start
     */
    public static void scheduleDelayedSync(Context context, long delaySeconds) {
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Scheduling delayed sync work in " + delaySeconds + " seconds");
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(new Data.Builder()
                        .putString(SyncWorker.KEY_SYNC_TYPE, SyncWorker.SYNC_TYPE_PERIODIC)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                SyncWorker.WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                syncRequest);
    }

    /**
     * Cancel all scheduled sync work.
     *
     * @param context Application context
     */
    public static void cancelAllSyncWork(Context context) {
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Cancelling all sync work");
        }

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC);
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_IMMEDIATE);
    }
}
