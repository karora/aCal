/*
 * Copyright (C) 2026 Andrew McMillan
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

package org.davical.acal.service.connector;

import android.net.Uri;
import android.util.Log;

import org.davical.acal.Constants;

import org.json.JSONObject;

/**
 * Discovers an RFC 7808 (TZDIST) timezone service on a server, via its
 * {@code /.well-known/timezone} well-known URI.
 *
 * <p>This runs on a background thread (it makes a network request) and returns the
 * service <em>context</em> URL — the base that the timezone client appends
 * {@code /zones} and {@code /zones/{tzid}} to — or {@code null} if the host offers
 * no usable timezone service.</p>
 */
public class TimezoneServiceDiscovery {
    private static final String TAG = "aCal TzServiceDiscovery";

    /**
     * Probe {@code {schemeAuthority}/.well-known/timezone} for an RFC 7808 service.
     *
     * @param schemeAuthority the scheme and authority of the server, e.g. {@code https://host:8443}
     * @return the validated service context URL (e.g. {@code https://host/timezones}) or {@code null}
     */
    public static String discover(String schemeAuthority) {
        if (schemeAuthority == null) return null;
        try {
            AcalRequestor requestor = new AcalRequestor();
            requestor.interpretUriString(schemeAuthority + "/.well-known/timezone");

            // AcalRequestor follows redirects, so this lands on the service (its capabilities
            // resource on a compliant server) after the well-known redirect chain.
            JSONObject root = requestor.doJsonRequest("GET", null, null, null);
            int status = requestor.getStatusCode();
            if (root == null || status < 200 || status >= 300) {
                if (Constants.LOG_DEBUG)
                    Log.println(Constants.LOGD, TAG, "No timezone service at " + schemeAuthority + " (status " + status + ")");
                return null;
            }
            // RFC 7808 capabilities documents carry an "actions" array; treat its presence as
            // confirmation this is really a TZDIST service rather than some other JSON.
            if (!root.has("actions")) {
                if (Constants.LOG_DEBUG)
                    Log.println(Constants.LOGD, TAG, "Response from " + requestor.fullUrl() + " is not a TZDIST capabilities document");
                return null;
            }

            // The context is the final URL minus the resource we landed on. The well-known
            // redirects to the context root, which a compliant server in turn redirects to
            // its /capabilities (and /zones is the list resource), so strip either suffix.
            Uri landed = Uri.parse(requestor.fullUrl());
            String path = landed.getPath();
            if (path != null) {
                for (String suffix : new String[]{"/capabilities", "/zones"}) {
                    if (path.endsWith(suffix)) {
                        path = path.substring(0, path.length() - suffix.length());
                        break;
                    }
                }
            }
            while (path != null && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String context = landed.getScheme() + "://" + landed.getAuthority() + (path == null ? "" : path);
            Log.println(Constants.LOGI, TAG, "Discovered timezone service context: " + context);
            return context;
        } catch (Exception e) {
            Log.println(Constants.LOGI, TAG, "No timezone service discovered at " + schemeAuthority + ": " + e.getMessage());
            return null;
        }
    }
}
