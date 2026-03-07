package com.morphoss.acal;

import java.util.concurrent.ConcurrentHashMap;

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
import com.morphoss.acal.service.SyncWorkScheduler;
import com.morphoss.acal.service.connector.AcalConnectionPool;

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
        // Initialize connection pool with app context for certificate storage
        AcalConnectionPool.initialize(this);
        // Schedule periodic sync work using WorkManager
        SyncWorkScheduler.schedulePeriodicSync(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Delete old channels to ensure settings are updated
            manager.deleteNotificationChannel(Constants.ALARM_NOTIFICATION_CHANNEL_ID);
            manager.deleteNotificationChannel(Constants.PRE_ALARM_NOTIFICATION_CHANNEL_ID);
            manager.deleteNotificationChannel(Constants.ACTIVE_NOTIFICATION_CHANNEL_ID);

            // Main notification channel: heads-up, notification sound, short vibration
            NotificationChannel main = new NotificationChannel(
                Constants.ALARM_NOTIFICATION_CHANNEL_ID,
                "Calendar Reminders",
                NotificationManager.IMPORTANCE_HIGH
            );
            Uri notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            main.setSound(notifSound, audioAttributes);
            main.enableVibration(true);
            main.setVibrationPattern(new long[]{0, 250});
            main.setBypassDnd(false);
            manager.createNotificationChannel(main);

            // Pre-alarm channel: silent, upcoming event reminder
            NotificationChannel pre = new NotificationChannel(
                Constants.PRE_ALARM_NOTIFICATION_CHANNEL_ID,
                "Upcoming Event Reminders",
                NotificationManager.IMPORTANCE_LOW
            );
            pre.setSound(null, null);
            pre.enableVibration(false);
            manager.createNotificationChannel(pre);

            // Active event channel: silent, shown while event is active
            NotificationChannel active = new NotificationChannel(
                Constants.ACTIVE_NOTIFICATION_CHANNEL_ID,
                "Active Events",
                NotificationManager.IMPORTANCE_LOW
            );
            active.setSound(null, null);
            active.enableVibration(false);
            manager.createNotificationChannel(active);

            // Certificate pinning channel: requires user action, high importance
            NotificationChannel certPin = new NotificationChannel(
                Constants.CERT_PIN_NOTIFICATION_CHANNEL_ID,
                "Certificate Verification",
                NotificationManager.IMPORTANCE_HIGH
            );
            certPin.setDescription("Alerts when a server certificate needs verification");
            certPin.setSound(null, null);
            certPin.enableVibration(false);
            manager.createNotificationChannel(certPin);
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
    	prefs.edit().putString(key, value).apply();
	}

	public static boolean getPreferenceBoolean(String key, boolean defValue) {
    	if ( prefs == null )
    		prefs = PreferenceManager.getDefaultSharedPreferences(s_instance);
    	return prefs.getBoolean(key, defValue);
	}

	final private static ConcurrentHashMap<String,String> zoneAliasCache = new ConcurrentHashMap<String,String>();
	public static String getOlsonFromAlias(String alias) {
	    return zoneAliasCache.computeIfAbsent(alias, key -> {
    	    ContentResolver cr = getContext().getContentResolver();
    	    Uri resolveAliasUri = Uri.withAppendedPath(Uri.withAppendedPath(Timezones.CONTENT_URI, "resolve_alias"), Uri.encode(key));
            Log.d(TAG,"Resolving timezone: "+resolveAliasUri.toString());
    	    Cursor c = cr.query( resolveAliasUri, null, null, null, null );
    	    if ( c != null ) {
    	        try {
    	            if ( c.getCount() > 0 ) {
    	                c.moveToFirst();
    	                return c.getString(0);
    	            }
    	        } finally {
    	            c.close();
    	        }
    	    }
    	    return null;
	    });
	}

    public static Resource getResourceFromDatabase(long resource_id) {
        return Resource.fromDatabase(getContext(), resource_id);
    }
}
