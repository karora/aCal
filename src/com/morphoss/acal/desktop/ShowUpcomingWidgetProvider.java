package com.morphoss.acal.desktop;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RemoteViews;

import com.morphoss.acal.AcalDebug;
import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.R;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.activity.EventView;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.service.aCalService;

public class ShowUpcomingWidgetProvider extends AppWidgetProvider {
	
	public static final String TAG = "aCal ShowUpcomingWidgetProvider";
	
	public static final int NUMBER_OF_EVENTS_TO_SHOW = 4;
	public static final int NUM_DAYS_TO_LOOK_AHEAD = 7;
	
	public static final String TABLE = "show_upcoming_widget_data";
	
	public static final String FIELD_ID = "_id";
	public static final String FIELD_RESOURCE_ID = "resource_id";
	public static final String FIELD_ETAG = "etag";
	public static final String FIELD_COLOUR = "colour";
	public static final String FIELD_DTSTART = "dtstart";
	public static final String FIELD_DTEND = "dtend";
	public static final String FIELD_SUMMARY = "summary";

	public static final String SHOW_UPCOMING_WIDGET_IDS_KEY ="acalshowupcomingwidgetids";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		try {
			if ( intent.hasExtra(SHOW_UPCOMING_WIDGET_IDS_KEY) ) {
				int[] ids = intent.getExtras().getIntArray(SHOW_UPCOMING_WIDGET_IDS_KEY);
				this.onUpdate(context, AppWidgetManager.getInstance(context), ids);
				if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget onReceive ended update");
			}
			else
				super.onReceive(context, intent);
		}
		catch ( Exception e ) {
			Log.w(TAG, "Unexpected exception in OnReceive", e);
		}
	}

	
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
				"onUpdate Called...");
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget onUpdate started");
		for (int widgetId : appWidgetIds) {

			Intent updateIntent = new Intent();	
			updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			updateIntent.putExtra(SHOW_UPCOMING_WIDGET_IDS_KEY, appWidgetIds);

			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
					"Processing for widget id: "+widgetId);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			boolean prefer24Hour = prefs.getBoolean(context.getString(R.string.prefTwelveTwentyfour),false);
			
			
			RemoteViews views = new RemoteViews(context.getPackageName(),R.layout.show_upcoming_widget_layout);
			views.removeAllViews(R.id.upcoming_container);
			
			//Used to calculate when we should trigger an update.
			long timeOfNextEventEnd = Long.MAX_VALUE;
			long timeOfNextEventStart = Long.MAX_VALUE;

			//Remove events that have finished
			cleanOld(context);
			
			//Get Data
			ContentValues[] cvs = getCurrentData(context);
			for (int  i = 0; i<NUMBER_OF_EVENTS_TO_SHOW; i++) {
				if (cvs[i] != null) {
					if (Constants.LOG_VERBOSE) Log.println(Constants.LOGV, TAG, "Processing event "+i);
					try {
						AcalDateTime dtstart = AcalDateTime.fromMillis(cvs[i].getAsLong(FIELD_DTSTART)).shiftTimeZone(TimeZone.getDefault().getID());
						AcalDateTime dtend = AcalDateTime.fromMillis(cvs[i].getAsLong(FIELD_DTEND)).shiftTimeZone(TimeZone.getDefault().getID());

						int rid = cvs[i].getAsInteger(FIELD_RESOURCE_ID);
						
						//set up on click intent
						Intent viewEvent = new Intent(context, EventView.class);
						viewEvent.putExtra(EventView.RESOURCE_ID_KEY, rid);
						viewEvent.putExtra(EventView.DTSTART_KEY,dtstart.getMillis());
						PendingIntent onClickIntent = PendingIntent.getActivity(context, i, viewEvent, PendingIntent.FLAG_UPDATE_CURRENT);
						
						//inflate row
						RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.show_upcoming_widget_base_row);
	
						LayoutInflater lf = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						ShowUpcomingRowLayout rowLayout = (ShowUpcomingRowLayout)lf.inflate(R.layout.show_upcoming_widget_custom_row, null);
	
						row.setImageViewBitmap(R.id.upcoming_row_image, rowLayout.setData(
								cvs[i].getAsInteger(ShowUpcomingWidgetProvider.FIELD_COLOUR),
								cvs[i].getAsString(ShowUpcomingWidgetProvider.FIELD_SUMMARY),
								getNiceDateTime(context,dtstart,dtend,prefer24Hour) ));

						row.setOnClickPendingIntent(R.id.upcoming_row, onClickIntent);
						row.setOnClickPendingIntent(R.id.upcoming_row_image, onClickIntent);
						
						if (timeOfNextEventEnd > dtend.getMillis()) timeOfNextEventEnd = dtend.getMillis();
						if (timeOfNextEventStart > dtstart.getMillis()) timeOfNextEventStart = dtstart.getMillis();
						
						//addview
						views.addView(R.id.upcoming_container, row);
					}
					catch( Exception e ) {
						Log.e(TAG,"Error getting widget datetime",e);
					}
					
				}
				else if ( i==0 ) {
					RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.show_upcoming_widget_base_row);
					
					LayoutInflater lf = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					ShowUpcomingRowLayout rowLayout = (ShowUpcomingRowLayout)lf.inflate(R.layout.show_upcoming_widget_custom_row, null);

					row.setImageViewBitmap(R.id.upcoming_row_image,
							rowLayout.setData(Color.BLACK,context.getString(R.string.noScheduledEvents), "" ));
					
					//addview
					views.addView(R.id.upcoming_container, row);
				}
			}
			
			//set on click
			//views.setOnClickPendingIntent(R.id.upcoming_container, pendingIntent);
			
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG, 
					"Processing widget "+widgetId+" completed.");
		
			appWidgetManager.updateAppWidget(widgetId, views);
			
			//schedule alarm to wake us if next event starts/ends within refresh period (30 mins);
			//ignore start if negative
			long now = System.currentTimeMillis();
			long start = timeOfNextEventStart - now;
			long end = timeOfNextEventEnd - now;
			long timeTillNextAlarm = end;
			if (start > 0 && start<end) timeTillNextAlarm = start;
			
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG, 
					"Next Event start/finish = "+timeTillNextAlarm);
			if (timeTillNextAlarm< 1800000L) {
				if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG, 
						"Setting update alarm for "+(timeTillNextAlarm/1000)+" seconds from now. due to event starting or ending");
				// Get the AlarmManager service
				 AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				 am.set(AlarmManager.RTC, System.currentTimeMillis()+timeTillNextAlarm, pendingIntent);
			} else {
				if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG, 
						"No Events starting or ending in the next 30 mins, not setting alarm.");
			}
		}
	}
	
	//Clean any events from the DB that have ended
	public static void cleanOld(Context context) {
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget cleanOld");
		long endTime = System.currentTimeMillis();
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getReadableDatabase();
		int res = db.delete(TABLE, FIELD_DTEND+" <= ?", new String[]{""+endTime});
		db.close();
		dbhelper.close();
		if (res >  0) aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_SHOW_UPCOMING_WIDGET_UPDATE,null,null));
		if (Constants.LOG_DEBUG && Constants.debugWidget)  Log.println(Constants.LOGD, TAG,
				"Deleted "+res+" event(s) that have ended.");
	}
	
	/**
	 * Get the current contents of the DB table and return as an array of ContentValues
	 * Array Size is ALWAYS NUMBER_POF_EVENTS_TO_SHOW, however there maybe NULL values if the 
	 * number of rows in the DB is different. Returned array is in order of the _ID field, which should
	 * be the same order in which events were added.
	 * 
	 * @param context
	 * @return
	 */
	public synchronized static ContentValues[] getCurrentData(Context context) {
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget getCurrentData");
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD, TAG, "Retrieving current data");
		ContentValues[] cvs = new ContentValues[NUMBER_OF_EVENTS_TO_SHOW];
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getReadableDatabase();
		
		Cursor cursor = db.query(TABLE, null, null, null, null, null, FIELD_ID+" ASC");
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			for (int i=0; !cursor.isAfterLast() && i<NUMBER_OF_EVENTS_TO_SHOW; cursor.moveToNext(), i++) {
				cvs[i] = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(cursor, cvs[i]);
				if (Constants.LOG_VERBOSE && Constants.debugWidget) Log.println(Constants.LOGV, TAG,
						"Loaded event "+i+" from db");
			}
		}
		cursor.close();
		db.close();
		dbhelper.close();
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget getCurrentData done");
		return cvs;
	}

	public synchronized static void checkIfUpdateRequired(Context context, List<AcalEvent> currentEvents) {
		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG, 
				"Checking to see if widget update is required...");

		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget checkIfUpdateRequired");
		//Turn events into content vals for easier processing
		ContentValues[] cvs = new ContentValues[currentEvents.size()];
		for (int i = 0; i< currentEvents.size(); i++) {
			ContentValues cv = getCVFromEvent(context, currentEvents.get(i));
			if (cv != null)	cvs[i] = cv;
			else break;
		}
		
		if (hasDataChanged(context, cvs)){
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
					"Data change detected, Updating DB");
			updateData(context, cvs);
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
					"Sending update broadcast");
			StaticHelpers.updateWidgets(context, ShowUpcomingWidgetProvider.class);
		}
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget checkIfUpdateRequired done");
	}
	
	/**
	 * Returns true if the list of events provided does not match the contents of the database
	 * @param context
	 * @param currentEvents
	 * @return
	 */
	public synchronized static boolean hasDataChanged(Context context, ContentValues[] newData) {
		ContentValues[] oldData = getCurrentData(context);
		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
				"Comparing current data to db");
		
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget hasDataChanged");
		if (oldData.length != newData.length) return true;
		
		for (int i = 0; i< NUMBER_OF_EVENTS_TO_SHOW && i< oldData.length; i++) {
			if (oldData[i] == null && newData[i] == null) continue; //both are null
			if (oldData[i] == null || newData[i] == null) return true; //only one is null

			//check all fields for change
			if (		oldData[i].getAsInteger(FIELD_RESOURCE_ID) != newData[i].getAsInteger(FIELD_RESOURCE_ID) ||  
						!oldData[i].getAsString(FIELD_ETAG).equals(newData[i].getAsString(FIELD_ETAG)) ||
						oldData[i].getAsInteger(FIELD_COLOUR) != newData[i].getAsInteger(FIELD_COLOUR) ||
						oldData[i].getAsLong(FIELD_DTSTART) != newData[i].getAsLong(FIELD_DTSTART) ||
						oldData[i].getAsLong(FIELD_DTEND) != newData[i].getAsLong(FIELD_DTEND) ||
						!oldData[i].getAsString(FIELD_SUMMARY).equals(newData[i].getAsString(FIELD_SUMMARY)))
				return true;
			
			//check to see if this event has ended
			long now = new AcalDateTime().getMillis();
			if (oldData[i].getAsLong(FIELD_DTEND) <= now) return true;
		}
		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
				"Data does not appear to have changed.");
		return false;
	}
	
	/**
	 * Overwrites the current database table with the list of events
	 * @param context
	 * @param currentEvents
	 */
	public static synchronized void updateData(Context context, ContentValues[] cvs) {
		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
				"Writing new values to DB");
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget upateData");
		AcalDBHelper dbhelper = new AcalDBHelper(context);
		SQLiteDatabase db = dbhelper.getWritableDatabase();
		db.beginTransaction();
		
		//1st, clear existing data
		db.delete(TABLE, null, null);
		db.yieldIfContendedSafely();

		//2nd Add each event
		for (ContentValues cv : cvs) {
			db.insert(TABLE, null, cv);
			db.yieldIfContendedSafely();
		}
		
		db.setTransactionSuccessful();
		db.endTransaction(); 
		
		db.close(); 
		if ( Constants.debugHeap ) AcalDebug.heapDebug(TAG, "Widget upateData done");
	}

	
	public synchronized static ContentValues getCVFromEvent(Context context, AcalEvent event) {
		ContentValues cv = new ContentValues();

		String etag = getEtag(context, event.resourceId);
		if (etag == null) {
			//etag fail
			return null;
		}
		
		cv.put(FIELD_RESOURCE_ID, event.resourceId);
		cv.put(FIELD_ETAG, etag);
		cv.put(FIELD_COLOUR,event.getColour());
		cv.put(FIELD_DTSTART, event.getStart().applyLocalTimeZone().getMillis());
		cv.put(FIELD_DTEND, event.getEnd().applyLocalTimeZone().getMillis());
		cv.put(FIELD_SUMMARY, event.getSummary());
		
		return cv;
	}

	
	public synchronized static String getEtag(Context context, int resource_id) {
		if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
				"Getting Etag for resource "+resource_id);
		ContentResolver cr = context.getContentResolver();
		String ret = null;
		Cursor c = null;
		try {
			c = cr.query(DavResources.CONTENT_URI, new String[]{DavResources.ETAG}, DavResources._ID+" = ?", new String[]{resource_id+""}, null);
			if (c.getCount() < 1) {
				if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
						"Could not find value in DB");
				c.close();
				return null;
			}
			c.moveToFirst();
			ret = c.getString(0);
		} catch (Exception e) {
			if (Constants.LOG_DEBUG && Constants.debugWidget) Log.println(Constants.LOGD, TAG,
					"Exception occured: "+e);
		}
		if (c != null) c.close();
		return ret;
		
	}
	
	
	private String getNiceDateTime(Context context, AcalDateTime start, AcalDateTime end, boolean use24HourFormat) {
		AcalDateTime now = new AcalDateTime().applyLocalTimeZone();

		String time;
		DateFormat format;
		if (use24HourFormat) format = new SimpleDateFormat("HH:mm");
		else format = new SimpleDateFormat("hh:mmaa");
		if ( start.getMillis() <= System.currentTimeMillis() ) {
			if ( end.getMillis() < System.currentTimeMillis() ) {
				return context.getString(R.string.Finished);
			}
			else if (end.getYear() == now.getYear() && end.getYearDay() == now.getYearDay())
				time = format.format(end.toJavaDate()).toLowerCase();
			else { 
				if ( now.getDurationTo(end).getDays() == 0 ) {
					return context.getString(R.string.Today);
				}
				else {
					//multiday event
					time = now.getDurationTo(end).getDays()+" "+context.getString(R.string.days);
				}
			}
		}
		else time = format.format(start.toJavaDate()).toLowerCase();

		if (start.getMillis() <= System.currentTimeMillis() ) {
			return context.getString(R.string.endsAt, time); ///Event is occuring now
		}
		else if ( start.getEpochDay() == now.getEpochDay() ) {
			return time;  // Leave day of week off to identify it as 'today'
		}
		else {
			StringBuilder result = new StringBuilder(time);
			result.append(" (");
			int dow = start.getWeekDay();
			switch (dow) {
				case AcalDateTime.MONDAY: result.append(context.getString(R.string.Mon)); break;
				case AcalDateTime.TUESDAY: result.append(context.getString(R.string.Tue)); break;
				case AcalDateTime.WEDNESDAY: result.append(context.getString(R.string.Wed)); break;
				case AcalDateTime.THURSDAY: result.append(context.getString(R.string.Thu)); break;
				case AcalDateTime.FRIDAY: result.append(context.getString(R.string.Fri)); break;
				case AcalDateTime.SATURDAY: result.append(context.getString(R.string.Sat)); break;
				case AcalDateTime.SUNDAY: result.append(context.getString(R.string.Sun)); break;
			}
			result.append(")");
			return result.toString();
		}
	}
	
}
