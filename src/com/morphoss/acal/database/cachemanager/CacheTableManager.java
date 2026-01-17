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

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.CacheModifier;
import com.morphoss.acal.database.CacheWindow;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.ProviderTableManager;
import com.morphoss.acal.desktop.ShowUpcomingWidgetProvider;
import com.morphoss.acal.providers.CacheDataProvider;

/**
 * Encapsulates all database operations for the event cache.
 * Extracted from CacheManager to promote single responsibility principle.
 *
 * @author Chris Noldus
 */
public final class CacheTableManager extends ProviderTableManager {

    public static final String TAG = "acal EventCacheProcessor";

    public static final String TABLE = "event_cache";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_RESOURCE_ID = "resource_id";
    public static final String FIELD_RESOURCE_TYPE = "resource_type";
    public static final String FIELD_RECURRENCE_ID = "recurrence_id";
    public static final String FIELD_CID = "collection_id";
    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_DTSTART = "dtstart";
    public static final String FIELD_DTEND = "dtend";
    public static final String FIELD_COMPLETED = "completed";
    public static final String FIELD_DTSTART_FLOAT = "dtstartfloat";
    public static final String FIELD_DTEND_FLOAT = "dtendfloat";
    public static final String FIELD_COMPLETE_FLOAT = "completedfloat";
    public static final String FIELD_FLAGS = "flags";

    public static final String RESOURCE_TYPE_VEVENT = "VEVENT";
    public static final String RESOURCE_TYPE_VTODO = "VTODO";
    public static final String RESOURCE_TYPE_VJOURNAL = "VJOURNAL";

    private boolean windowOnly = false;

    // Dependencies injected from CacheManager
    private final CacheManagerCallback callback;
    private final CopyOnWriteArraySet<CacheChangedListener> listeners;

    /**
     * Callback interface for CacheManager to provide dependencies.
     */
    public interface CacheManagerCallback extends CacheModifier {
        CacheWindow getWindow();
        void setWindow(CacheWindow window);
        void retrieveRange();
        void checkDefaultWindow();
        long getLookForward();
        long getLookBack();
        long getMaxSize();
        long getMinPaddingBack();
        long getMinPaddingForward();
        long getIncrement();
        int getDefMonthsBefore();
        int getDefMonthsAfter();
    }

    /**
     * Generate a new instance of this processor.
     * WARNING: Only 1 instance of this class should ever exist. If multiple instances are created bizarre
     * side effects may occur, including Database corruption and program instability
     */
    CacheTableManager(Context context, CacheManagerCallback callback,
                      CopyOnWriteArraySet<CacheChangedListener> listeners) {
        super(context);
        this.callback = callback;
        this.listeners = listeners;
    }

    @Override
    protected Uri getCallUri() {
        return CacheDataProvider.CONTENT_URI;
    }

    @Override
    protected Uri getQueryUri() {
        return CacheDataProvider.CONTENT_URI;
    }

    /**
     * Process a CacheRequest. This class will provide an interface to the CacheRequest giving it access to the Cache Table.
     * Will warn if given request has misused the DB, but will not cause program to exit. Will ensure that database state is kept
     * consistent.
     */
    public synchronized void process(CacheRequest r) {
        this.windowOnly = false;
        try {
            r.process(this);
            if (this.inTx) {
                this.endTx();
                throw new CacheProcessingException("Process started a transaction without ending it!\n    Request: " + r.getClass().getSimpleName());
            }
        } catch (CacheProcessingException e) {
            Log.e(TAG, "Error Processing Cache Request: " + Log.getStackTraceString(e));
        } catch (Exception e) {
            Log.e(TAG, "INVALID TERMINATION while processing Cache Request: " + Log.getStackTraceString(e));
        }
    }

    public void setWindowOnlyTrue() {
        this.windowOnly = true;
    }

    /**
     * Called when table is deemed to have been corrupted.
     */
    void clearCache() {
        this.beginTx();
        this.delete(null, null);
        this.setTxSuccessful();
        this.endTx();
        callback.setWindow(new CacheWindow(callback.getLookForward(), callback.getLookBack(),
                callback.getMaxSize(), callback.getMinPaddingBack(),
                callback.getMinPaddingForward(), callback.getIncrement(),
                callback, new AcalDateTime()));
        Log.println(Constants.LOGW, TAG, "Cache cleared of possibly corrupt data.");
    }

    public void rebuildCache() {
        clearCache();
        callback.checkDefaultWindow();
    }

    /**
     * One specific common query for the cache is to fetch rows for a particular range of dates.
     *
     * @param range Must not be null, or have either end null
     * @return ArrayList of matching cache rows
     */
    public ArrayList<ContentValues> queryInRange(AcalDateRange range, String cacheObjectType) {
        if (CacheManager.DEBUG && Constants.LOG_DEBUG)
            Log.println(Constants.LOGD, CacheManager.TAG,
                    "Selecting cache objects in " + range + ": \nSELECT * FROM event_cache WHERE " +
                            CacheManager.whereClauseForRange(range, cacheObjectType));

        return this.query(null, CacheManager.whereClauseForRange(range, cacheObjectType),
                null, CacheTableManager.FIELD_DTSTART + " ASC");
    }

    /**
     * Checks that the window has been populated with the requested range
     * range can be NULL in which case the default range is used.
     * If the range is NOT covered, a request is made to resource
     * manager to get the required data.
     * Returns whether or not the cache fully covers a specified (or default) range
     */
    public boolean checkWindow(AcalDateRange requestedRange) {
        CacheWindow window = callback.getWindow();
        if (CacheManager.DEBUG && Constants.LOG_DEBUG) {
            Log.println(Constants.LOGD, TAG, "Checking Cache Window: Request " + requestedRange);
            Log.println(Constants.LOGD, TAG, "Checking Cache Window: Current Window:" + window);
        }
        boolean ret = false;
        if (window == null) clearCache();
        window = callback.getWindow();
        if (window.isWithinWindow(requestedRange))
            ret = true;

        // We might as well look a bit beyond the requested range just to be safe.
        AcalDateRange preCache = new AcalDateRange(
                requestedRange.start.clone().addMonths(callback.getDefMonthsBefore()),
                requestedRange.end.clone().addMonths(callback.getDefMonthsAfter())
        );

        window.addToRequestedRange(preCache);

        // Expand as needed
        callback.retrieveRange();

        return ret;
    }

    // Never ever ever ever call cacheChanged on listeners anywhere else.
    @Override
    public void dataChanged(ArrayList<DataChangeEvent> changes) {
        if (changes.isEmpty()) return;
        synchronized (listeners) {
            for (CacheChangedListener listener : listeners) {
                CacheChangedEvent cce = new CacheChangedEvent(new ArrayList<DataChangeEvent>(changes), windowOnly);
                listener.cacheChanged(cce);
            }
        }
        // Update widgets
        StaticHelpers.updateWidgets(context, ShowUpcomingWidgetProvider.class);
    }

    public void resourceDeleted(long rid) {
        this.delete(FIELD_RESOURCE_ID + " = ?", new String[]{rid + ""});
    }

    public void updateWindowToInclude(AcalDateRange range) {
        callback.getWindow().expandWindow(range);
    }

    public void removeRangeFromWindow(AcalDateRange range) {
        callback.getWindow().reduceWindow(range);
    }
}
