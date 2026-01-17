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

package com.morphoss.acal.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.morphoss.acal.service.SyncWorkScheduler;

/**
 * Receiver for BOOT_COMPLETED broadcast.
 * Schedules periodic sync work using WorkManager instead of directly starting
 * a service, which is required on modern Android versions.
 */
public class StartUpIntentReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Schedule periodic sync work using WorkManager
		// This is the modern approach that works with background execution limits
		SyncWorkScheduler.schedulePeriodicSync(context);

		// Also schedule an immediate sync to start syncing after boot
		SyncWorkScheduler.scheduleImmediateSync(context);
	}

}
