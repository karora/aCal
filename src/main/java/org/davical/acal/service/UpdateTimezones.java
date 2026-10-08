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

package org.davical.acal.service;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.davical.acal.AcalApplication;
import org.davical.acal.Constants;
import org.davical.acal.PrefNames;
import org.davical.acal.R;
import org.davical.acal.StaticHelpers;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.davacal.VComponent;
import org.davical.acal.providers.Timezones;
import org.davical.acal.service.connector.AcalConnectionPool;
import org.davical.acal.service.connector.AcalRequestor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

public class UpdateTimezones extends ServiceJob {
    private static final String TAG = "aCal UpdateTimezones";
    private aCalService context;
    private ContentResolver cr;
    private AcalRequestor requestor;
    private String tzServerBaseUrl;

    private boolean deferMe = false;

    /**
     * Constructor
     *
     * @param serverId
     * @param Context
     */
    public UpdateTimezones(long timeToExecute) {
        TIME_TO_EXECUTE = timeToExecute;
    }

    /**
     * Loop through all active collections and
     */
    public void run(aCalService context) {
        this.context = context;
        this.cr = context.getContentResolver();
        tzServerBaseUrl = context.getPreferenceString(PrefNames.tzServerBaseUrl, context.getString(R.string.defaultTzServerBaseUrl));
        tzServerBaseUrl = normaliseContext(tzServerBaseUrl);
        this.requestor = new AcalRequestor();

        if (Constants.LOG_DEBUG) Log.d(TAG, "Refreshing Timezone data from " + tzServerBaseUrl);
        refreshTimezoneData();
        if (Constants.LOG_DEBUG) Log.d(TAG, "Timezone refresh complete.");
        scheduleNextUpdate();
    }

    /**
     * Strip a trailing slash (and, if present, a trailing /.well-known/timezone) so the
     * value is the RFC 7808 service context that we append /zones etc. to.
     */
    private String normaliseContext(String url) {
        if (url == null) return null;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    /**
     * RFC 7808 "list" action: GET {context}/zones returns
     * {"synctoken":…,"timezones":[{tzid,last-modified,…},…]}.
     */
    private String zonesListUrl() {
        return tzServerBaseUrl + "/zones";
    }

    /**
     * RFC 7808 "get" action: GET {context}/zones/{tzid} returns the VTIMEZONE.
     * The tzid is a path; keep its '/' separators but escape spaces and the like
     * (the zone list can include names such as "New Zealand Standard Time").
     */
    private String zoneGetUrl(String tzid) {
        return tzServerBaseUrl + "/zones/" + Uri.encode(tzid, "/");
    }

    private void refreshTimezoneData() {
        try {
            HashMap<String, Long> currentZones = new HashMap<String, Long>();
            HashMap<String, Long> updatedZones = new HashMap<String, Long>();
            HashMap<String, Long> insertedZones = new HashMap<String, Long>();
            Cursor allZones = cr.query(Timezones.CONTENT_URI, new String[]{Timezones.TZID, Timezones.LAST_MODIFIED}, null, null, null);
            Long maxModified = 0L;
            for (allZones.moveToFirst(); !allZones.isAfterLast(); allZones.moveToNext()) {
                if (Constants.LOG_VERBOSE)
                    Log.println(Constants.LOGV, TAG, "Found existing zone of '" + allZones.getString(0) + "' modified: " + AcalDateTime.fromMillis(allZones.getLong(1) * 1000L).toString());
                currentZones.put(allZones.getString(0), allZones.getLong(1));
                if (allZones.getLong(1) > maxModified) maxModified = allZones.getLong(1);
            }
            final int localZoneCount = currentZones.size();
            AcalDateTime mostRecentChange = AcalDateTime.getUTCInstance().setEpoch(maxModified);
            Log.println(Constants.LOGI, TAG, "Found " + allZones.getCount() + " existing timezones, most recent change on " + mostRecentChange.toString());
            if (allZones.getCount() > 350 && mostRecentChange.after(AcalDateTime.getUTCInstance().addDays(-30))) {
                Log.println(Constants.LOGI, TAG, "Skipping update - our database is pretty recent");
                return;
            }

            JSONObject root = null;
            requestor.interpretUriString(zonesListUrl());
            try {
                root = requestor.doJsonRequest("GET", null, null, null);
            } catch (SSLHandshakeException e) {
                Log.e(TAG, "SSLHandshakeException while fetching timezone data: not loading timezones");
            }
            if (requestor.wasRedirected()) {
                // The server redirected the /zones request; recover the service context by
                // stripping the trailing /zones, and remember it for subsequent /zones/{tzid} fetches.
                Uri tzUri = Uri.parse(requestor.fullUrl());
                String redirectedPath = tzUri.getPath();
                if (redirectedPath != null && redirectedPath.endsWith("/zones"))
                    redirectedPath = redirectedPath.substring(0, redirectedPath.length() - "/zones".length());
                String redirectedContext = normaliseContext(tzUri.getScheme() + "://" + tzUri.getAuthority() + redirectedPath);
                if (Constants.debugTimeZone && Constants.LOG_DEBUG)
                    Log.println(Constants.LOGD, TAG, "Redirected to Timezone Server context " + redirectedContext);
                tzServerBaseUrl = redirectedContext;
                AcalApplication.setPreferenceString(PrefNames.tzServerBaseUrl, redirectedContext);
            }
            if (requestor.getStatusCode() >= 399) {
                Log.println(Constants.LOGI, TAG, "Bad response " + requestor.getStatusCode() + " from Timezone Server at " + zonesListUrl());
            }
            if (root == null) {
                Log.println(Constants.LOGI, TAG, "No JSON from GET " + zonesListUrl());
                return;
            }


            String tzid;
            String tzData = "";
            long lastModified;
            StringBuilder localNames;
            StringBuilder aliases;
            ContentValues zoneValues = new ContentValues();

            // RFC 7808 returns "synctoken" on the zone list (the old draft returned "dtstamp").
            // We rely on per-zone last-modified for change detection, so the token is informational.
            JSONArray tzArray = root.getJSONArray("timezones");
            for (int i = 0; i < tzArray.length(); i++) {
                JSONObject zoneNode = tzArray.getJSONObject(i);
                tzid = zoneNode.getString("tzid");
                if (updatedZones.containsKey(tzid) || insertedZones.containsKey(tzid)) continue;

                if (Constants.debugTimeZone && Constants.LOG_DEBUG)
                    Log.println(Constants.LOGD, TAG, "Working on " + tzid);

                lastModified = AcalDateTime.fromString(zoneNode.getString("last-modified")).getEpoch();
                if (currentZones.containsKey(tzid) && currentZones.get(tzid) <= lastModified) {
                    currentZones.remove(tzid);
                    continue;
                }

                tzData = getTimeZone(tzid);
                if (tzData == null) continue;

                localNames = new StringBuilder();
                try {
                    JSONArray nameNodes = zoneNode.getJSONArray("local_names");
                    for (int j = 0; j < nameNodes.length(); j++) {
                        if (localNames.length() > 0) localNames.append("\n");
                        localNames.append(nameNodes.getJSONObject(j).getString("lang")).append('~').append(nameNodes.getJSONObject(j).getString("lname"));
                    }
                } catch (JSONException je) {
                    Log.w(TAG, "JSON Exception", je);
                }

                aliases = new StringBuilder();
                try {
                    JSONArray aliasNodes = zoneNode.getJSONArray("aliases");
                    for (int j = 0; j < aliasNodes.length(); j++) {
                        if (aliases.length() > 0) aliases.append("\n");
                        aliases.append(aliasNodes.getString(j));
                    }
                } catch (JSONException je) {
                    Log.w(TAG, "JSON Exception", je);
                }

                zoneValues.put(Timezones.TZID, tzid);
                zoneValues.put(Timezones.ZONE_DATA, tzData);
                zoneValues.put(Timezones.LAST_MODIFIED, lastModified);
                zoneValues.put(Timezones.TZ_NAMES, localNames.toString());
                zoneValues.put(Timezones.TZID_ALIASES, aliases.toString());

                Uri tzUri = Uri.withAppendedPath(Timezones.CONTENT_URI, "tzid/" + StaticHelpers.urlescape(tzid, false));

                if (currentZones.containsKey(tzid)) {
                    if (cr.update(tzUri, zoneValues, null, null) != 1) {
                        Log.e(TAG, "Failed update for TZID '" + tzid + "'");
                    }
                    updatedZones.put(tzid, currentZones.get(tzid));
                    currentZones.remove(tzid);
                } else {
                    if (cr.insert(tzUri, zoneValues) == null)
                        Log.e(TAG, "Failed insert for TZID '" + tzid + "'");
                    insertedZones.put(tzid, currentZones.get(tzid));
                }

                if (context.workWaiting()) {
                    Log.println(Constants.LOGI, TAG, "Something is waiting - deferring timezone sync until later.");
                    deferMe = true;
                    break;
                }
                // Let other stuff have a chance
                Thread.sleep(350);
            }
            int removed = 0;

            // Zones still in currentZones are ones the server's list did NOT mention, i.e. candidates
            // for deletion. Guard against a sparse or broken server (or one pointed at the wrong path)
            // wiping the local set: only prune if the server's list looks reasonably complete.
            int serverZoneCount = tzArray.length();
            boolean serverListLooksComplete = serverZoneCount >= 100 || serverZoneCount >= localZoneCount;
            if (currentZones.size() > 0 && !serverListLooksComplete) {
                Log.println(Constants.LOGW, TAG, "Not pruning " + currentZones.size() + " local zone(s): server listed only "
                        + serverZoneCount + " zone(s) (have " + localZoneCount + " locally) — list looks incomplete.");
            } else if (currentZones.size() > 0) {
                StringBuilder s = new StringBuilder();
                for (String tz : currentZones.keySet()) {
                    if (s.length() > 0) s.append(',');
                    s.append("'").append(tz).append("'");
                }
                removed = cr.delete(Timezones.CONTENT_URI, Timezones.TZID + " IN (" + s + ")", null);
            }

            Log.println(Constants.LOGI, TAG, "Updated data for " + updatedZones.size() + " zones, added data for " + insertedZones.size() + " new zones, removed data for " + removed);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }


    private String getTimeZone(String tzid) {
        requestor.interpretUriString(zoneGetUrl(tzid));
        InputStream is = null;
        StringBuilder getResponse = new StringBuilder();
        try {
            is = requestor.doRequest("GET", null, null, null);
            if (requestor.getStatusCode() != 200) {
                Log.println(Constants.LOGI, TAG, "" + requestor.getStatusCode() + " response from Timezone Server at " + zoneGetUrl(tzid));
                return null;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is), AcalConnectionPool.DEFAULT_BUFFER_SIZE);
            String line;
            while ((line = r.readLine()) != null) {
                getResponse.append(line).append("\n");
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "Auto-generated catch block", e);
        } catch (IOException e) {
            Log.w(TAG, "Auto-generated catch block", e);
        } finally {
            try {
                is.close();
            } catch (Exception e) {
            }
            ;
        }
        try {
            VComponent vc = VComponent.createComponentFromBlob(getResponse.toString());
            List<VComponent> children = vc.getChildren();
            return children.get(0).getCurrentBlob();
        } catch (Exception e) {
            Log.w(TAG, "Auto-generated catch block", e);
            return null;
        }
    }

    private void scheduleNextUpdate() {
        this.TIME_TO_EXECUTE = System.currentTimeMillis() + (deferMe ? 90000 : 86400000 * 7);
//		this.TIME_TO_EXECUTE = System.currentTimeMillis() + 90000;
        Log.println(Constants.LOGV, TAG, "Scheduling next instance at " + AcalDateTime.fromMillis(this.TIME_TO_EXECUTE).fmtIcal());
        context.addWorkerJob(this);
    }


    @Override
    public String getDescription() {
        return "Refreshing timezones";
    }
}
