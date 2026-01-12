package com.morphoss.acal;

import java.util.HashMap;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.providers.Timezones;

public class AcalApplication extends Application {

	public static final String TAG = "AcalApplication";

    private static AcalApplication s_instance;
    private static SharedPreferences prefs;

    public AcalApplication(){
    	super();

    	s_instance = this;

    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Delete old channel to ensure settings are updated
            // (Android caches channel settings and won't update them otherwise)
            manager.deleteNotificationChannel(Constants.ALARM_NOTIFICATION_CHANNEL_ID);

            NotificationChannel channel = new NotificationChannel(
                Constants.ALARM_NOTIFICATION_CHANNEL_ID,
                "Calendar Alarms",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for calendar event alarms");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            channel.enableLights(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            // Set alarm sound with proper audio attributes
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            channel.setSound(alarmSound, audioAttributes);

            // Allow this channel to bypass Do Not Disturb
            channel.setBypassDnd(true);

            manager.createNotificationChannel(channel);
        }
    }

    private static Context getContext(){
        return s_instance;
    }

    public static String getResourceString(int resId){
        return getContext().getString(resId);
    }

	public static String getPreferenceString(String key, String defValue) {
    	if ( prefs == null )
    		prefs = PreferenceManager.getDefaultSharedPreferences(s_instance);
    	return prefs.getString(key, defValue);
	}

	public static void setPreferenceString(String key, String value) {
    	if ( prefs == null )
    		prefs = PreferenceManager.getDefaultSharedPreferences(s_instance);
    	prefs.edit().putString(key, value).commit();
	}

	public static boolean getPreferenceBoolean(String key, boolean defValue) {
    	if ( prefs == null )
    		prefs = PreferenceManager.getDefaultSharedPreferences(s_instance);
    	return prefs.getBoolean(key, defValue);
	}

	final private static HashMap<String,String> zoneAliasCache = new HashMap<String,String>();
	public static String getOlsonFromAlias(String alias) {
	    if ( ! zoneAliasCache.containsKey(alias) ) {
    	    ContentResolver cr = getContext().getContentResolver();
    	    Uri resolveAliasUri = Uri.withAppendedPath(Uri.withAppendedPath(Timezones.CONTENT_URI, "resolve_alias"), Uri.encode(alias));
            Log.d(TAG,"Resolving timezone: "+resolveAliasUri.toString());
    	    Cursor c = cr.query( resolveAliasUri, null, null, null, null );
    	    if ( c != null ) {
    	        if ( c.getCount() > 0 ) {
    	            c.moveToFirst();
    	            zoneAliasCache.put(alias,c.getString(0));
    	        }
    	        c.close();
    	    }
	    }
	    return zoneAliasCache.get(alias);
	}

    public static Resource getResourceFromDatabase(long resource_id) {
        return Resource.fromDatabase(getContext(), resource_id);
    }
}
