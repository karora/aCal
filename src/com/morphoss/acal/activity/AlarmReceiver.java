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

package com.morphoss.acal.activity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;

/**
 * BroadcastReceiver that handles alarm triggers and posts a full-screen notification.
 * This is required on Android 10+ due to Background Activity Launch restrictions.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final int ALARM_NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm received, posting full-screen notification");

        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Check if we can use full-screen intents (Android 14+ requires permission)
        boolean canUseFullScreen = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            canUseFullScreen = notificationManager.canUseFullScreenIntent();
            if (!canUseFullScreen) {
                Log.w(TAG, "Full-screen intent permission not granted. User needs to enable in Settings > Apps > aCal > Notifications");
            }
        }

        // Create intent to launch AlarmActivity
        Intent activityIntent = new Intent(context, AlarmActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Get the default alarm sound
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        // Build the notification with full-screen intent
        Notification.Builder builder = new Notification.Builder(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(Constants.ALARM_NOTIFICATION_CHANNEL_ID);
        }

        builder.setSmallIcon(R.drawable.icon)
            .setContentTitle("aCal Alarm")
            .setContentText("Calendar alarm is firing")
            .setContentIntent(fullScreenIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC);

        // Add full-screen intent if permission is available
        if (canUseFullScreen) {
            builder.setFullScreenIntent(fullScreenIntent, true);
        }

        // Add sound and vibration for the notification itself
        // (in case full-screen doesn't launch immediately)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Pre-Oreo: set sound and vibration on the notification
            builder.setSound(alarmSound);
            builder.setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000});
        }
        // On Oreo+, sound and vibration are controlled by the notification channel

        Notification notification = builder.build();

        // Make sure the notification keeps playing sound
        notification.flags |= Notification.FLAG_INSISTENT;

        Log.i(TAG, "Posting alarm notification (canUseFullScreen=" + canUseFullScreen + ")");

        // Check if notifications are enabled for this channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = notificationManager.getNotificationChannel(Constants.ALARM_NOTIFICATION_CHANNEL_ID);
            if (channel != null) {
                Log.i(TAG, "Channel importance: " + channel.getImportance() + ", sound: " + channel.getSound());
            } else {
                Log.e(TAG, "Notification channel is NULL!");
            }
        }

        notificationManager.notify(ALARM_NOTIFICATION_ID, notification);
        Log.i(TAG, "Notification posted with ID " + ALARM_NOTIFICATION_ID);
    }
}
