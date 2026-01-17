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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.alarmmanager.AlarmQueueManager;
import com.morphoss.acal.database.alarmmanager.IAlarmQueueManager;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.ICacheManager;
import com.morphoss.acal.database.resourcesmanager.IResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.di.ServiceRegistry;

/**
 * Main service for aCal background operations.
 * Handles AIDL binding for IPC and manages the WorkerClass for job processing.
 * Uses WorkManager for periodic sync scheduling instead of the deprecated IntentService.
 */
public class aCalService extends Service {

	private ServiceRequest.Stub serviceRequest;
	private WorkerClass worker;
	public static final String TAG = "aCalService";
	public static String aCalVersion = "aCal/1.0"; // Updated at start of program.

	private final static long serviceStartedAt = System.currentTimeMillis();
	private ResourceManager rm;
	private CacheManager cm;
	private AlarmQueueManager am;
	private MemoryMonitor memoryMonitor;

	private static SharedPreferences prefs = null;


	public void onCreate() {
		super.onCreate();

		aCalVersion = getString(R.string.appName) + "/";
		try {
			aCalVersion += getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException e) {
			Log.e(TAG,"Can't find our good self in the PackageManager!");
			Log.e(TAG,Log.getStackTraceString(e));
		}

		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		startService();
	}


	private synchronized void startService() {

		rm = ResourceManager.getInstance(this);
		cm = CacheManager.getInstance(this);
		am = AlarmQueueManager.getInstance(this);

		worker = WorkerClass.getInstance(this);
		memoryMonitor = new MemoryMonitor();

		// Create the service request handler with extracted implementation
		serviceRequest = new ServiceRequestHandlerImpl(worker, this);

		// Schedule immediate sync of any changes to the server
		worker.addJobAndWake(new SyncChangesToServer());

		// Start sync running for all active collections
		SynchronisationJobs.startCollectionSync(worker, this, 35000L);

		if ( ! Constants.DISABLE_FEATURE_TZSERVER_SUPPORT ) {
			// Start periodic syncing of timezone data
			worker.addJobAndWake(new UpdateTimezones(15000L));
		}
	}



	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleCommand(intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return Service.START_STICKY;
	}

	private void handleCommand( Intent inRequest ) {

        // Always schedule a service Restart in two hours time.  This will only
        // happen if we don't get called before that time and defer it further.
        scheduleServiceRestart(7200);

        if ( inRequest == null ) return;
        if ( inRequest.hasExtra("RESTARTED") ) {
            Log.i(TAG,AcalDateTime.getInstance().fmtIcal() + " This is a scheduled restart of aCalService");
        }
		if ( inRequest.hasExtra("UISTARTED") ) {
			// The UI is currently starting, so we might schedule some stuff
			// to happen soon.
			long uiStarted = inRequest.getLongExtra("UISTARTED", System.currentTimeMillis());
			if ( serviceStartedAt > uiStarted ) return; // Not if everything just started!

			// Tell the dataService to rebuild it's caches, just to be sure.
			if ( Constants.LOG_DEBUG )
				Log.i(TAG,"UI Started, requesting internal cache revalidation.");

			worker.resetWorker();

			ServiceJob job = new SynchronisationJobs(SynchronisationJobs.CACHE_RESYNC);
			job.TIME_TO_EXECUTE = 5000L;
			worker.addJobAndWake(job);

			// Start sync running for all active collections
			SynchronisationJobs.startCollectionSync(worker, this, 25000L);
		}
	}



	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG, "On destroy called. Killing worker thread.");
		//Ensure database is closed properly and worker is terminated.
		if ( worker != null ) worker.killWorker();
		worker = null;
		am.close();
		am = null;
		rm.close();
		cm.close();
		cm = null;
		rm = null;
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG, "Worker killed.");
	}



	private synchronized void scheduleServiceRestart(long secsInFuture) {
		// Use WorkManager for reliable background scheduling instead of AlarmManager
		SyncWorkScheduler.scheduleDelayedSync(getApplicationContext(), secsInFuture);
		Log.i(TAG, AcalDateTime.getInstance().fmtIcal() + ": Scheduling aCalService restart in "+secsInFuture+" seconds via WorkManager.");
	}


	//@Override
	public IBinder onBind(Intent arg0) {
		return serviceRequest;
	}

	public void addWorkerJob(ServiceJob s) {
		if ( memoryMonitor != null && memoryMonitor.shouldRestartService() ) {
			scheduleServiceRestart(memoryMonitor.getRestartDelaySeconds());
	        this.stopSelf();
		}
		else {
			if ( worker == null ) startService();
			this.worker.addJobAndWake(s);
		}
	}

	public boolean workWaiting() {
	    return worker.workWaiting();
	}

	public String getPreferenceString(String key, String defValue) {
    	if ( prefs == null )
    		prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	return prefs.getString(key, defValue);
	}
}

