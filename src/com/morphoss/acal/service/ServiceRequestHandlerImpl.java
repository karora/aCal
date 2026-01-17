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
import android.os.RemoteException;

/**
 * Handles AIDL service requests for IPC communication.
 * Extracted from aCalService to promote single responsibility principle.
 *
 * This class processes requests from other components/apps that need to
 * trigger synchronization operations.
 */
public class ServiceRequestHandlerImpl extends ServiceRequest.Stub {

    private final IWorkerClass worker;
    private final Context context;

    /**
     * Creates a new ServiceRequestHandler.
     *
     * @param worker The worker class to submit jobs to
     * @param context The service context for collection sync operations
     */
    public ServiceRequestHandlerImpl(IWorkerClass worker, Context context) {
        this.worker = worker;
        this.context = context;
    }

    @Override
    public void discoverHomeSets() throws RemoteException {
        ServiceJob job = new SynchronisationJobs(SynchronisationJobs.HOME_SET_DISCOVERY);
        job.TIME_TO_EXECUTE = System.currentTimeMillis();
        worker.addJobAndWake(job);
    }

    @Override
    public void updateCollectionsFromHomeSets() throws RemoteException {
        ServiceJob job = new SynchronisationJobs(SynchronisationJobs.HOME_SETS_UPDATE);
        job.TIME_TO_EXECUTE = System.currentTimeMillis();
        worker.addJobAndWake(job);
    }

    @Override
    public void fullResync() throws RemoteException {
        ServiceJob[] jobs = new ServiceJob[2];
        jobs[0] = new SynchronisationJobs(SynchronisationJobs.HOME_SET_DISCOVERY);
        jobs[1] = new SynchronisationJobs(SynchronisationJobs.HOME_SETS_UPDATE);
        worker.addJobsAndWake(jobs);
        SynchronisationJobs.startCollectionSync(worker, context, 15000L);
    }

    @Override
    public void revertDatabase() throws RemoteException {
        worker.addJobAndWake(new DebugDatabase(DebugDatabase.REVERT));
    }

    @Override
    public void saveDatabase() throws RemoteException {
        worker.addJobAndWake(new DebugDatabase(DebugDatabase.SAVE));
    }

    @Override
    public void homeSetDiscovery(int server) throws RemoteException {
        HomeSetDiscovery job = new HomeSetDiscovery(server);
        worker.addJobAndWake(job);
    }

    @Override
    public void syncCollectionNow(long collectionId) throws RemoteException {
        SyncCollectionContents job = new SyncCollectionContents(collectionId, true);
        worker.addJobAndWake(job);
    }

    @Override
    public void fullCollectionResync(long collectionId) throws RemoteException {
        InitialCollectionSync job = new InitialCollectionSync(collectionId);
        worker.addJobAndWake(job);
    }
}
