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

package org.davical.acal.activity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.R;
import org.davical.acal.aCal;
import org.davical.acal.aCalNotes;
import org.davical.acal.aCalTasks;
import org.davical.acal.database.alarmmanager.ALARM_STATE;
import org.davical.acal.database.alarmmanager.AlarmQueueManager;
import org.davical.acal.database.alarmmanager.AlarmRow;
import org.davical.acal.database.alarmmanager.requests.ARUpdateAlarmState;
import org.davical.acal.davacal.VComponent;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * BroadcastReceiver that handles alarm triggers and posts standard notifications.
 * These are calendar event reminders — not alarm-clock alarms — so they respect
 * Do Not Disturb and never take over the phone screen.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                dispatch(context, intent);
            } finally {
                pr.finish();
            }
        }).start();
    }

    private void dispatch(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) action = Constants.ALARM_ACTION_FIRE;

        Log.i(TAG, "Alarm dispatch: " + action);

        long rowId = intent.getLongExtra(Constants.ALARM_EXTRA_ROW_ID, -1);
        String title = intent.getStringExtra(Constants.ALARM_EXTRA_TITLE);
        if (title == null) title = "Calendar Event";

        NotificationManager nm = getNotifManager(context);

        if (Constants.ALARM_ACTION_PRE.equals(action)) {
            handlePre(context, nm, rowId, title, intent);
        } else if (Constants.ALARM_ACTION_DISMISS.equals(action)) {
            handleDismiss(context, nm, rowId, intent);
        } else if (Constants.ALARM_ACTION_SNOOZE.equals(action)) {
            handleSnooze(context, nm, rowId, intent);
        } else {
            // Default: ALARM_ACTION_FIRE
            handleFire(context, nm, rowId, title, intent);
        }
    }

    private void handlePre(Context context, NotificationManager nm, long rowId, String title,
                           Intent intent) {
        long eventTime = intent.getLongExtra(Constants.ALARM_EXTRA_EVENT_TIME,
                intent.getLongExtra(Constants.ALARM_EXTRA_TTF, System.currentTimeMillis()));
        Notification.Builder builder = new Notification.Builder(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(Constants.PRE_ALARM_NOTIFICATION_CHANNEL_ID);
        }
        builder.setSmallIcon(R.drawable.icon)
               .setContentTitle(title)
               .setContentText(describeEventTime(eventTime))
               .setContentIntent(buildViewPendingIntent(context, intent, rowId))
               .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        nm.notify((int) rowId, builder.build());
    }

    private void handleFire(Context context, NotificationManager nm,
                            long rowId, String title, Intent intent) {
        // Record that this alarm has fired *before* posting, so it is never
        // rescheduled/refired, and so the next pending alarm gets its wakeup
        // scheduled. Blocking: the process may be killed once we return.
        AlarmRow row = buildAlarmRowFromIntent(intent);
        if (row != null) {
            AlarmQueueManager.getInstance(context)
                    .sendBlockingRequest(new ARUpdateAlarmState(row, ALARM_STATE.FIRED));
        }

        // Cancel pre-alarm notification
        nm.cancel((int) rowId);

        long eventTime = intent.getLongExtra(Constants.ALARM_EXTRA_EVENT_TIME,
                intent.getLongExtra(Constants.ALARM_EXTRA_TTF, System.currentTimeMillis()));

        // Snooze action
        Intent snoozeIntent = new Intent(intent);
        snoozeIntent.setAction(Constants.ALARM_ACTION_SNOOZE);
        PendingIntent snoozePI = PendingIntent.getBroadcast(context,
                (int) rowId + 100_000, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Dismiss action
        Intent dismissIntent = new Intent(intent);
        dismissIntent.setAction(Constants.ALARM_ACTION_DISMISS);
        PendingIntent dismissPI = PendingIntent.getBroadcast(context,
                (int) rowId + 200_000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tap to open the event this alarm is for
        PendingIntent viewPI = buildViewPendingIntent(context, intent, rowId);

        Notification.Builder builder = new Notification.Builder(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(Constants.ALARM_NOTIFICATION_CHANNEL_ID);
        }
        builder.setSmallIcon(R.drawable.icon)
               .setContentTitle(title)
               .setContentText(describeEventTime(eventTime))
               .setContentIntent(viewPI)
               .setAutoCancel(true)
               .setOngoing(false)
               .addAction(R.drawable.icon, "Snooze", snoozePI)
               .addAction(R.drawable.icon, "Dismiss", dismissPI);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_EVENT);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        nm.notify((int) rowId + 100_000, builder.build());

        // Active event notification (silent, ongoing while event is active)
        Intent doneIntent = new Intent(intent);
        doneIntent.setAction(Constants.ALARM_ACTION_DISMISS);
        PendingIntent donePI = PendingIntent.getBroadcast(context,
                (int) rowId + 300_000, doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder activeBuilder = new Notification.Builder(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeBuilder.setChannelId(Constants.ACTIVE_NOTIFICATION_CHANNEL_ID);
        }
        activeBuilder.setSmallIcon(R.drawable.icon)
                     .setContentTitle("Now: " + title)
                     .setContentIntent(viewPI)
                     .setOngoing(true)
                     .addAction(R.drawable.icon, "Done", donePI);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            activeBuilder.setPriority(Notification.PRIORITY_LOW);
        }
        nm.notify((int) rowId + 200_000, activeBuilder.build());
    }

    /**
     * PendingIntent opening the component the alarm belongs to: the event view for
     * events, or the tasks/notes list for todos and journals.
     */
    private PendingIntent buildViewPendingIntent(Context context, Intent intent, long rowId) {
        String component = intent.getStringExtra(Constants.ALARM_EXTRA_COMPONENT);
        long rid = intent.getLongExtra(Constants.ALARM_EXTRA_RID, -1);
        String rrid = intent.getStringExtra(Constants.ALARM_EXTRA_RRID);

        Intent viewIntent;
        if (VComponent.VTODO.equals(component)) {
            viewIntent = new Intent(context, aCalTasks.class);
        } else if (VComponent.VJOURNAL.equals(component)) {
            viewIntent = new Intent(context, aCalNotes.class);
        } else if (rid >= 0) {
            viewIntent = new Intent(context, EventView.class);
            viewIntent.putExtra(EventView.RESOURCE_ID_KEY, rid);
            viewIntent.putExtra(EventView.RECURRENCE_ID_KEY, rrid);
        } else {
            viewIntent = new Intent(context, aCal.class);
        }
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, (int) rowId, viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Human-readable event start, e.g. "Starts at 14:30 (in 25 min)" or "Started at 09:00".
     */
    private String describeEventTime(long eventTime) {
        Calendar event = Calendar.getInstance();
        event.setTimeInMillis(eventTime);
        Calendar now = Calendar.getInstance();
        boolean sameDay = event.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && event.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
        String pattern = sameDay ? "HH:mm" : "EEE HH:mm";
        String timeStr = new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(eventTime));

        long minutes = Math.round((eventTime - System.currentTimeMillis()) / 60000.0);
        if (minutes > 1) {
            String in = (minutes >= 60)
                    ? (minutes / 60) + "h " + (minutes % 60) + "m"
                    : minutes + " min";
            return "Starts at " + timeStr + " (in " + in + ")";
        }
        if (minutes >= -1) return "Starts now (" + timeStr + ")";
        return "Started at " + timeStr;
    }

    private void handleDismiss(Context context, NotificationManager nm,
                               long rowId, Intent intent) {
        nm.cancel((int) rowId + 100_000);
        nm.cancel((int) rowId + 200_000);
        AlarmRow row = buildAlarmRowFromIntent(intent);
        if (row != null) {
            // Blocking: the process may be killed as soon as onReceive completes.
            AlarmQueueManager.getInstance(context)
                    .sendBlockingRequest(new ARUpdateAlarmState(row, ALARM_STATE.DISMISSED));
        }
    }

    private void handleSnooze(Context context, NotificationManager nm,
                              long rowId, Intent intent) {
        nm.cancel((int) rowId + 100_000);
        AlarmRow row = buildAlarmRowFromIntent(intent);
        if (row != null) {
            // Blocking: the process may be killed as soon as onReceive completes.
            AlarmQueueManager.getInstance(context)
                    .sendBlockingRequest(new ARUpdateAlarmState(row, ALARM_STATE.SNOOZED));
        }
    }

    private AlarmRow buildAlarmRowFromIntent(Intent intent) {
        long rowId = intent.getLongExtra(Constants.ALARM_EXTRA_ROW_ID, -1);
        if (rowId < 0) return null;
        long baseTtf = intent.getLongExtra(Constants.ALARM_EXTRA_BASE_TTF, 0);
        long ttf     = intent.getLongExtra(Constants.ALARM_EXTRA_TTF, 0);
        long rid     = intent.getLongExtra(Constants.ALARM_EXTRA_RID, 0);
        String rrid  = intent.getStringExtra(Constants.ALARM_EXTRA_RRID);
        if (rrid == null) rrid = "";
        int stateOrd = intent.getIntExtra(Constants.ALARM_EXTRA_STATE, 0);
        ALARM_STATE state = ALARM_STATE.values()[stateOrd];
        String blob  = intent.getStringExtra(Constants.ALARM_EXTRA_BLOB);
        if (blob == null) blob = "";
        return new AlarmRow(rowId, baseTtf, ttf, rid, rrid, state, blob);
    }

    private NotificationManager getNotifManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
