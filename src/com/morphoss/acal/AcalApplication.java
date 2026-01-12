package com.morphoss.acal;

import java.util.HashMap;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
            NotificationChannel channel = new NotificationChannel(
                Constants.ALARM_NOTIFICATION_CHANNEL_ID,
                "Calendar Alarms",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for calendar event alarms");
            channel.enableVibration(true);
            channel.enableLights(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
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
