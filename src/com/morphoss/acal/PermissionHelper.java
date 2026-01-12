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

package com.morphoss.acal;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for handling runtime permissions on Android 6.0+ (API 23+).
 *
 * Prior to API 23, permissions were granted at install time. Starting with
 * API 23, dangerous permissions must be requested at runtime.
 */
public class PermissionHelper {

    public static final int PERMISSION_REQUEST_CODE = 1001;

    /**
     * The core permissions required for aCal to function.
     * These are "dangerous" permissions that require runtime consent on API 23+.
     */
    private static final String[] REQUIRED_PERMISSIONS_BASE = {
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    };

    /**
     * Get the required permissions for the current API level.
     * Storage permission is only needed on API 28 and below; scoped storage
     * on API 29+ means WRITE_EXTERNAL_STORAGE has no effect.
     */
    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return new String[] {
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        return REQUIRED_PERMISSIONS_BASE;
    }

    /**
     * Check if runtime permission handling is needed (API 23+).
     */
    public static boolean needsRuntimePermissions() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * Check if all required permissions are granted.
     *
     * @param activity The activity context
     * @return true if all permissions are granted, false otherwise
     */
    public static boolean hasAllPermissions(Activity activity) {
        if (!needsRuntimePermissions()) {
            return true;
        }

        for (String permission : getRequiredPermissions()) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the list of permissions that are not yet granted.
     *
     * @param activity The activity context
     * @return Array of permission strings that need to be requested
     */
    public static String[] getMissingPermissions(Activity activity) {
        if (!needsRuntimePermissions()) {
            return new String[0];
        }

        List<String> missing = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    /**
     * Request all missing permissions.
     *
     * @param activity The activity making the request
     * @return true if a request was made, false if all permissions already granted
     */
    public static boolean requestMissingPermissions(Activity activity) {
        String[] missing = getMissingPermissions(activity);
        if (missing.length == 0) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(missing, PERMISSION_REQUEST_CODE);
        }
        return true;
    }

    /**
     * Check if the permission request result indicates all permissions were granted.
     *
     * @param requestCode The request code from onRequestPermissionsResult
     * @param grantResults The grant results array
     * @return true if this was our request and all permissions were granted
     */
    public static boolean allPermissionsGranted(int requestCode, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return false;
        }

        if (grantResults.length == 0) {
            return false;
        }

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if we should show rationale for any of the missing permissions.
     * This is true if the user has previously denied the permission.
     *
     * @param activity The activity context
     * @return true if rationale should be shown for at least one permission
     */
    public static boolean shouldShowRationale(Activity activity) {
        if (!needsRuntimePermissions()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : getRequiredPermissions()) {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get a user-friendly description of why permissions are needed.
     *
     * @return A string explaining why the app needs these permissions
     */
    public static String getPermissionRationale() {
        return "aCal needs access to your calendar and contacts to sync with your CalDAV/CardDAV server.";
    }

    // ========== Alarm-related permissions ==========

    public static final int ALARM_PERMISSION_REQUEST_CODE = 1002;

    /**
     * Check if POST_NOTIFICATIONS permission is needed and not granted.
     * Required on Android 13+ (API 33) to show any notifications.
     */
    public static boolean needsNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    /**
     * Request the POST_NOTIFICATIONS permission.
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                ALARM_PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * Check if exact alarm permission is needed and not granted.
     * Required on Android 12+ (API 31) for exact alarms.
     */
    public static boolean needsExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return !alarmManager.canScheduleExactAlarms();
        }
        return false;
    }

    /**
     * Get the intent to open exact alarm settings.
     */
    public static Intent getExactAlarmSettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        }
        return null;
    }

    /**
     * Check if full-screen intent permission is needed and not granted.
     * Required on Android 14+ (API 34) for full-screen alarm notifications.
     */
    public static boolean needsFullScreenIntentPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return !nm.canUseFullScreenIntent();
        }
        return false;
    }

    /**
     * Get the intent to open app notification settings where full-screen intent can be enabled.
     */
    public static Intent getFullScreenIntentSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        return intent;
    }

    /**
     * Check if any alarm-related permissions are missing.
     */
    public static boolean hasAlarmPermissionIssues(Activity activity) {
        return needsNotificationPermission(activity)
            || needsExactAlarmPermission(activity)
            || needsFullScreenIntentPermission(activity);
    }

    /**
     * Get a description of what alarm permissions are missing.
     */
    public static String getAlarmPermissionStatus(Activity activity) {
        StringBuilder sb = new StringBuilder();

        if (needsNotificationPermission(activity)) {
            sb.append("- Notification permission required\n");
        }
        if (needsExactAlarmPermission(activity)) {
            sb.append("- Exact alarm permission required (for precise alarm timing)\n");
        }
        if (needsFullScreenIntentPermission(activity)) {
            sb.append("- Full-screen notification permission required (for alarm display)\n");
        }

        return sb.toString();
    }
}
