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

import java.util.concurrent.Semaphore;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.widget.Toast;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.CacheWindow;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.providers.CacheDataProvider;

/**
 * Handles persistence of cache state to the database.
 * Extracted from CacheManager to promote single responsibility principle.
 *
 * Manages loading and saving of cache window state and meta lock management.
 */
public class CacheStatePersistence {

    private static final String TAG = "CacheStatePersistence";

    // DB Constants
    private static final String FIELD_ID = "_id";
    private static final String FIELD_START = "dtstart";
    private static final String FIELD_END = "dtend";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_CLOSED = "closed";

    private static final int CLOSED_STATE_DIRTY = 0;
    private static final int CLOSED_STATE_CLEAN = 1;

    // Meta lock management
    private static final Semaphore lockSem = new Semaphore(1, true);
    private static volatile boolean lockdb = false;

    private final Context context;

    public CacheStatePersistence(Context context) {
        this.context = context;
    }

    /**
     * Acquire meta lock for database operations.
     */
    public synchronized void acquireMetaLock() {
        try {
            lockSem.acquire();
        } catch (InterruptedException e1) {
        }
        int count = 0;
        while (lockdb && count++ < 500) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
            }
        }
        if (count > 499) {
            lockSem.release();
            Log.e(TAG, "Unable to acquire metalock.", new Exception("Stack Trace"));
        }
        if (lockdb) throw new IllegalStateException("Can't acquire a lock that hasn't been released!");
        lockdb = true;
        lockSem.release();
    }

    /**
     * Release meta lock after database operations.
     */
    public synchronized void releaseMetaLock() {
        if (!lockdb) Log.e(TAG, "Lock released already!", new Exception("Stack Trace"));
        lockdb = false;
    }

    /**
     * Load state from database on startup.
     * If the cache wasn't closed properly, clears it and returns null.
     *
     * @param cacheTableManager The cache table manager for clearing if needed
     * @param cacheParams Cache configuration parameters
     * @param cacheModifier Cache modifier callback
     * @param resourceManager Resource manager for adding listener
     * @param listener Resource changed listener
     * @return The loaded cache window, or a new default window if state was dirty
     */
    public CacheWindow loadState(CacheTableManager cacheTableManager,
                                  CacheParams cacheParams,
                                  CacheTableManager.CacheManagerCallback cacheModifier,
                                  ResourceManager resourceManager,
                                  ResourceChangedListener listener) {
        ContentResolver cr = context.getContentResolver();
        ContentValues data = new ContentValues();
        long start;
        long end;
        AcalDateRange range = null;

        acquireMetaLock();

        // Load start/end range from meta table
        AcalDateTime defaultWindow = new AcalDateTime();
        Cursor mCursor = null;
        try {
            mCursor = cr.query(CacheDataProvider.META_URI, null, null, null, null);
        } catch (SQLiteException e) {
            Log.i(TAG, Log.getStackTraceString(e));
            releaseMetaLock();
            return createDefaultWindow(cacheParams, cacheModifier, defaultWindow);
        }

        int closedState = CLOSED_STATE_DIRTY;
        try {
            if (mCursor == null || mCursor.getCount() < 1) {
                Log.println(Constants.LOGI, TAG, "Initializing cache for first use.");
                data.put(FIELD_CLOSED, CLOSED_STATE_DIRTY);
                data.put(FIELD_COUNT, 0);
                data.put(FIELD_START, defaultWindow.getMillis());
                data.put(FIELD_END, defaultWindow.getMillis());
            } else {
                mCursor.moveToFirst();
                DatabaseUtils.cursorRowToContentValues(mCursor, data);
            }
            closedState = data.getAsInteger(FIELD_CLOSED);
            start = data.getAsLong(FIELD_START);
            end = data.getAsLong(FIELD_END);
            if (start >= 0 && end >= 0)
                range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
        } catch (Exception e) {
            Log.i(TAG, Log.getStackTraceString(e));
            data.put(FIELD_CLOSED, CLOSED_STATE_DIRTY);
            data.put(FIELD_COUNT, 0);
            start = defaultWindow.getMillis();
            end = defaultWindow.getMillis();
            data.put(FIELD_START, start);
            data.put(FIELD_END, end);
            range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
        } finally {
            if (mCursor != null) mCursor.close();
        }

        CacheWindow window;
        if (closedState == CLOSED_STATE_DIRTY) {
            Log.println(Constants.LOGI, TAG, "Application not closed correctly last time. Resetting cache.");
            Toast.makeText(context, "Rebuilding cache - It may take some time before events are visible.", Toast.LENGTH_LONG).show();
            cacheTableManager.clearCache();
            data.put(FIELD_COUNT, 0);
            start = defaultWindow.getMillis();
            end = defaultWindow.getMillis();
            data.put(FIELD_START, start);
            data.put(FIELD_END, end);
            range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
        }

        data.put(FIELD_CLOSED, CLOSED_STATE_CLEAN);
        cr.delete(CacheDataProvider.META_URI, null, null);
        data.remove(FIELD_ID);
        cr.insert(CacheDataProvider.META_URI, data);

        window = createDefaultWindow(cacheParams, cacheModifier, new AcalDateTime());
        if (range != null) window.setWindowSize(range);

        resourceManager.addListener(listener);
        releaseMetaLock();

        return window;
    }

    /**
     * Save state to database on shutdown.
     *
     * @param window The current cache window
     * @param resourceManager Resource manager for removing listener
     * @param listener Resource changed listener to remove
     */
    public void saveState(CacheWindow window,
                           ResourceManager resourceManager,
                           ResourceChangedListener listener) {
        ContentResolver cr = context.getContentResolver();
        ContentValues data = new ContentValues();
        data.put(FIELD_CLOSED, CLOSED_STATE_CLEAN);

        // Save start/end range to meta table
        acquireMetaLock();
        AcalDateRange windowRange = window != null ? window.getCurrentWindow() : null;
        if (windowRange != null) {
            data.put(FIELD_START, windowRange.start.getMillis());
            data.put(FIELD_END, windowRange.end.getMillis());
        } else {
            data.put(FIELD_START, -1);
            data.put(FIELD_END, -1);
        }

        // Set CLOSED to true
        cr.update(CacheDataProvider.META_URI, data, null, null);

        if (resourceManager != null && listener != null) {
            resourceManager.removeListener(listener);
        }
        releaseMetaLock();
    }

    /**
     * Set resource in transaction state.
     *
     * @param window Current cache window
     * @param inTxn Whether a transaction is in progress
     */
    public void setResourceInTx(CacheWindow window, boolean inTxn) {
        ContentValues data = new ContentValues();
        ContentResolver cr = context.getContentResolver();

        AcalDateRange currentRange = null;
        try {
            currentRange = window != null ? window.getCurrentWindow() : null;
        } catch (Exception e) {
        }
        if (currentRange == null) currentRange = new AcalDateRange(new AcalDateTime(), new AcalDateTime());

        acquireMetaLock();

        // Get current values
        Cursor cursor = null;
        try {
            cursor = cr.query(CacheDataProvider.META_URI, null, null, null, null);
            if (cursor.getCount() >= 1) {
                cursor.moveToFirst();
                DatabaseUtils.cursorRowToContentValues(cursor, data);
                cursor.close();
                cursor = null;
                data.put(FIELD_CLOSED, (inTxn ? CLOSED_STATE_DIRTY : CLOSED_STATE_CLEAN));
                if (!inTxn && window != null) {
                    data.put(FIELD_START, currentRange.start.getMillis());
                    data.put(FIELD_END, currentRange.end.getMillis());
                }
                cr.update(CacheDataProvider.META_URI, data, FIELD_ID + " = ?",
                        new String[]{data.getAsLong(FIELD_ID) + ""});
            } else {
                data = new ContentValues();
                data.put(FIELD_CLOSED, (inTxn ? CLOSED_STATE_DIRTY : CLOSED_STATE_CLEAN));
                data.put(FIELD_COUNT, 0);
                if (!inTxn && window != null) {
                    data.put(FIELD_START, currentRange.start.getMillis());
                    data.put(FIELD_END, currentRange.end.getMillis());
                } else {
                    long now = System.currentTimeMillis();
                    data.put(FIELD_START, now);
                    data.put(FIELD_END, now);
                }
                cr.insert(CacheDataProvider.META_URI, data);
            }
        } catch (Exception e) {
            Log.i(TAG, Log.getStackTraceString(e));
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
            releaseMetaLock();
        }
    }

    private CacheWindow createDefaultWindow(CacheParams params,
                                             CacheTableManager.CacheManagerCallback cacheModifier,
                                             AcalDateTime currentTime) {
        return new CacheWindow(params.lookForward, params.lookBack, params.maxSize,
                params.minPaddingBack, params.minPaddingForward, params.increment,
                cacheModifier, currentTime);
    }

    /**
     * Parameters for cache window configuration.
     */
    public static class CacheParams {
        public final long lookForward;
        public final long lookBack;
        public final long maxSize;
        public final long minPaddingBack;
        public final long minPaddingForward;
        public final long increment;

        public CacheParams(long lookForward, long lookBack, long maxSize,
                           long minPaddingBack, long minPaddingForward, long increment) {
            this.lookForward = lookForward;
            this.lookBack = lookBack;
            this.maxSize = maxSize;
            this.minPaddingBack = minPaddingBack;
            this.minPaddingForward = minPaddingForward;
            this.increment = increment;
        }
    }
}
