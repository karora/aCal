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
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.morphoss.acal.Constants;

/**
 * WorkManager-based worker for periodic sync operations.
 * Replaces the AlarmManager-based service restart scheduling with
 * WorkManager's reliable background execution.
 */
public class SyncWorker extends Worker {

    public static final String TAG = "SyncWorker";
    public static final String WORK_NAME_PERIODIC = "acal_periodic_sync";
    public static final String WORK_NAME_IMMEDIATE = "acal_immediate_sync";

    public static final String KEY_SYNC_TYPE = "sync_type";
    public static final String SYNC_TYPE_PERIODIC = "periodic";
    public static final String SYNC_TYPE_UI_STARTED = "ui_started";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "SyncWorker starting work");
        }

        try {
            String syncType = getInputData().getString(KEY_SYNC_TYPE);
            if (syncType == null) {
                syncType = SYNC_TYPE_PERIODIC;
            }

            Context context = getApplicationContext();

            // Start the aCalService to perform sync
            Intent serviceIntent = new Intent(context, aCalService.class);
            if (SYNC_TYPE_UI_STARTED.equals(syncType)) {
                serviceIntent.putExtra("UISTARTED", System.currentTimeMillis());
            } else {
                serviceIntent.putExtra("RESTARTED", System.currentTimeMillis());
            }

            context.startService(serviceIntent);

            if (Constants.LOG_DEBUG) {
                Log.d(TAG, "SyncWorker completed successfully");
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "SyncWorker failed: " + e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
            return Result.retry();
        }
    }
}
