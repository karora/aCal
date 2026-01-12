package com.morphoss.acal.database.alarmmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ConditionVariable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.activity.AlarmActivity;
import com.morphoss.acal.database.DMAction;
import com.morphoss.acal.database.DMInsertQuery;
import com.morphoss.acal.database.DMQueryList;
import com.morphoss.acal.database.DMUpdateQuery;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.ProviderTableManager;
import com.morphoss.acal.database.alarmmanager.requests.ARResourceChanged;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmRequest;
import com.morphoss.acal.database.alarmmanager.requesttypes.AlarmResponse;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequest;
import com.morphoss.acal.database.alarmmanager.requesttypes.BlockingAlarmRequestWithResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requests.RRGetUpcomingAlarms;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.providers.AlarmDataProvider;
import com.morphoss.acal.providers.CacheDataProvider;

/**
 * This manager manages the Alarm Database Table(s). It will listen to changes to resources and update the DB
 * automatically. AlarmRequests can be sent to query the table and to notify of changes in alarm state (e.g. dismiss/snooze)
 *
 * @author Chris Noldus
 *
 */
public class AlarmQueueManager implements Runnable, ResourceChangedListener  {

	//The current instance
	private static AlarmQueueManager instance = null;
	public static final String TAG = "aCal AlarmQueueManager";

	//Get an instance
	public synchronized static AlarmQueueManager getInstance(Context context) {
		if (instance == null) instance = new AlarmQueueManager(context);
		return instance;
	}

	//get and instance and add a callback handler to receive notfications of change
	//It is vital that classes remove their handlers when terminating
	public synchronized static AlarmQueueManager getInstance(Context context, AlarmChangedListener listener) {
		if (instance == null) {
			instance = new AlarmQueueManager(context);
		}
		instance.addListener(listener);
		return instance;
	}

	private final Context context;

	//ThreadManagement
	private final ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<AlarmRequest> queue = new ConcurrentLinkedQueue<AlarmRequest>();

	//Meta Table Management
	private static Semaphore lockSem = new Semaphore(1, true);
	private static volatile boolean lockdb = false;

	//DB Fields
    private static final String FIELD_ID = "_id";
    private static final String FIELD_CLOSED = "closed";

    private static final int CLOSED_STATE_DIRTY = 0;
    private static final int CLOSED_STATE_CLEAN = 1;

	//Comms
	private final CopyOnWriteArraySet<AlarmChangedListener> listeners = new CopyOnWriteArraySet<AlarmChangedListener>();
	private ResourceManager rm;

	//Request Processor Instance
	private AlarmTableManager ATMinstance;

	private AlarmTableManager getATMInstance() {
		if (instance == null) ATMinstance = new AlarmTableManager();
		return ATMinstance;
	}

	/**
	 * CacheManager needs a context to manage the DB. Should run under AcalService.
	 * Loadstate ensures that our DB is consistant and should be run before any resource
	 * modifications can be made by any other part of the system.
	 */
	private AlarmQueueManager(Context context) {
		this.context = context;
		this.ATMinstance = this.getATMInstance();
		rm = ResourceManager.getInstance(context);
		loadState();
		workerThread = new Thread(this);
		workerThread.start();

	}

	/**
	 * Add a lister to change events. Change events are fired whenever a change to the DB occurs
	 * @param ccl
	 */
	public void addListener(AlarmChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.add(ccl);
		}
	}

	/**
	 * Remove an existing listener. Listeners should always be removed when they no longer require changes.
	 * @param ccl
	 */
	public void removeListener(AlarmChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.remove(ccl);
		}
	}

    private synchronized static void acquireMetaLock() {
        try { lockSem.acquire(); } catch (InterruptedException e1) {}
        int count = 0;
        while (lockdb && count++ < 500) try { Thread.sleep(10); } catch (Exception e) { }
        if ( count > 499 ) {
            lockSem.release();
            throw new RuntimeException("Unable to acquire metalock.");
        }
        if (lockdb) throw new IllegalStateException("Cant acquire a lock that hasnt been released!");
        lockdb = true;
        lockSem.release();
    }

    private synchronized static void releaseMetaLock() {
        if (!lockdb) throw new IllegalStateException("Cant release a lock that hasnt been obtained!");
        lockdb = false;
    }

	/**
	 * Called on start up. if safe==false flush cache. set safe to false regardless.
	 */
	private void loadState() {
        ContentResolver cr = context.getContentResolver();
        ContentValues data = new ContentValues();

		acquireMetaLock();
        Cursor mCursor = null;
        try {
            mCursor = cr.query(AlarmDataProvider.META_URI, null, null, null, null);
        }
        catch( SQLiteException e ) {
            Log.i(TAG,Log.getStackTraceString(e));
            releaseMetaLock();
            return;
        }
        int closedState = CLOSED_STATE_DIRTY;
        try {
            if (mCursor == null || mCursor.getCount() < 1) {
                Log.println(Constants.LOGI,TAG, "Initializing cache for first use.");
			} else  {
				mCursor.moveToFirst();
				DatabaseUtils.cursorRowToContentValues(mCursor, data);
				closedState = data.getAsInteger(FIELD_CLOSED);
			}
		}
		catch( Exception e ) {
			Log.i(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( mCursor != null ) mCursor.close();
		}

		if ( closedState == CLOSED_STATE_DIRTY ) {
			Log.i(TAG, "Rebuiliding alarm cache.");
			rebuild();
		}
		data.put(FIELD_CLOSED, CLOSED_STATE_CLEAN);

        cr.delete(CacheDataProvider.META_URI, null, null);
        data.remove(FIELD_ID);
        cr.insert(CacheDataProvider.META_URI, data);

        rm.addListener(this);
		releaseMetaLock();

	}


    /**
     * MUST set SAFE to true or cache will be flushed on next load.
     * Nothing should be able to modify resources after this point.
     *
     */
    private void saveState() {
        ContentResolver cr = context.getContentResolver();
        ContentValues data = new ContentValues();
        data.put(FIELD_CLOSED, CLOSED_STATE_CLEAN);

        //save start/end range to meta table
        acquireMetaLock();

        // set CLOSED to true
        cr.update(CacheDataProvider.META_URI, data, null, null);

        //dereference ourself so GC can clean up
        instance = null;
        this.ATMinstance = null;
        rm.removeListener(this);
        rm = null;
        releaseMetaLock();
    }


	/**
	 * Ensures that this classes closes properly. MUST be called before it is terminated
	 */
	public void close() {
		this.running = false;
		//Keep waking worker thread until it dies
		while (workerThread.isAlive()) {
			threadHolder.open();
			Thread.yield();
			try { Thread.sleep(100); } catch (Exception e) { }
		}
		workerThread = null;
		saveState();
	}

	/**
	 * Forces AlarmManager to rebuild alarms from scratch. Should only be called if table has become invalid.
	 */
	private void rebuild() {
		ATMinstance.rebuild();
	}

	/**
	 * Method for responding to requests from activities.
	 */
	@Override
	public void run() {
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		while (running) {
			//do stuff
			while (!queue.isEmpty()) {
				AlarmRequest request = queue.poll();
				ATMinstance.process(request);
			}
			//Wait till next time
			threadHolder.close();
			threadHolder.block();
		}
	}

	/**
	 * A resource has changed. we need to see if this affects our table
	 */
	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		this.sendRequest(new ARResourceChanged(event));
	}

	/**
	 * Send a request to the AlarmManager. Requests are queued and processed a-synchronously. No guarantee is given as to
	 * the order of processing, so if sending multiple requests, consider potential race conditions.
	 * @param request
	 * @throws IllegalStateException thrown if close() has been called.
	 */
	public void sendRequest(AlarmRequest request) throws IllegalStateException {
		if (instance == null || this.workerThread == null || this.ATMinstance == null)
			throw new IllegalStateException("AM in illegal state - probably because sendRequest was called after close() has been called.");
		queue.offer(request);
		threadHolder.open();
	}

	public <E> AlarmResponse<E> sendBlockingRequest(BlockingAlarmRequestWithResponse<E> request) {
		queue.offer(request);
		threadHolder.open();
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (!request.isProcessed()) {
			try { Thread.sleep(10); } catch (Exception e) {	}
		}
		Thread.currentThread().setPriority(priority);
		return request.getResponse();
	}
	public void sendBlockingRequest(BlockingAlarmRequest request) {
		queue.offer(request);
		threadHolder.open();
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (!request.isProcessed()) {
			try { Thread.sleep(10); } catch (Exception e) {	}
		}
		Thread.currentThread().setPriority(priority);
	}


	public final class AlarmTableManager extends ProviderTableManager {
		/**
		 * Generate a new instance of this processor.
		 * WARNING: Only 1 instance of this class should ever exist. If multiple instances are created bizarre
		 * side affects may occur, including Database corruption and program instability
		 */

		// Change this to set how far back we look for alarms and database first time use/rebuild
		private static final int LOOKBACK_SECONDS = 300;		//default 5 minutes

		private static final String TAG = "aCal AlarmQueueManager";

		private final AlarmManager alarmManager;

		private AlarmTableManager() {
			super(AlarmQueueManager.this.context);
			alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		}

		@Override
        protected Uri getCallUri() {
            return AlarmDataProvider.CONTENT_URI;
        }

        @Override
        protected Uri getQueryUri() {
            return AlarmDataProvider.CONTENT_URI;
        }


		public void process(AlarmRequest request) {
			long start = System.currentTimeMillis();
			if (Constants.debugAlarms) {
				Log.d(TAG,"Processing "+request.getClass()+": "+request.getLogDescription());
			}
			try {
				request.process(this);
			} catch (AlarmProcessingException e) {
				Log.e(TAG, "Error Processing Alarm Request: "+Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG, "INVALID TERMINATION while processing Alarm Request: "+Log.getStackTraceString(e));
			}
			if (Constants.debugAlarms) {
				Log.d(TAG, "Processing of "+request.getClass()+" complete in "+(System.currentTimeMillis()-start)+"ms");
			}
		}

		@Override
		public void dataChanged(ArrayList<DataChangeEvent> changes) {
			AlarmChangedEvent event = new AlarmChangedEvent(changes);
			for (AlarmChangedListener acl : listeners) acl.alarmChanged(event);
		}


		//Custom operations

		/**
		 * Wipe and rebuild alarm table - called if it has become corrupted.
		 */
		public void rebuild() {
			Log.i(TAG, "Clearing Alarm Cache of possibly corrupt data and rebuilding...");
			//Display up to the last x hours of alarms.
			long lookForwardLimit = System.currentTimeMillis() + 86400000;

			//Step 1 - request a list of all resources so we can find the next alarm trigger for each
			RRGetUpcomingAlarms request = new RRGetUpcomingAlarms();
			rm.sendBlockingRequest(request);
			ArrayList<AlarmRow> alarms = request.getResponse().result();
			int count = 0;

			//For each alarm check whether or not it has not been dismissed
			DMQueryList list = new DMQueryList();
			for (AlarmRow alarm : alarms) {
			    ArrayList<ContentValues> dismissedAlarms
			        = super.query(
			                    new String[] {AlarmDataProvider.BASE_TIME_TO_FIRE },
			                    AlarmDataProvider.RESOURCE_ID+"="+alarm.resourceId+
            			            " AND " + AlarmDataProvider.RRID+"= '"+alarm.recurrenceId +"' " +
            			            " AND " + AlarmDataProvider.BASE_TIME_TO_FIRE+"="+alarm.baseTimeToFire+
            			            " AND " + AlarmDataProvider.STATE +"="+ ALARM_STATE.DISMISSED.ordinal(), null, null);
			    if ( dismissedAlarms.isEmpty() /* && alarm.getTimeToFire() < lookForwardLimit */ ) {
    				list.addAction(new DMInsertQuery(null, alarm.toContentValues()));
    				count++;
    				Log.i(TAG,"Alarm set : "+alarm.toString() );
			    }
			    else {
                    Log.i(TAG,"Skipping  : "+alarm.toString() );
			    }
			}

			//step 2 - begin db transaction, delete all existing and insert new list
			super.beginTx();
			super.delete(AlarmDataProvider.STATE +"!="+ ALARM_STATE.DISMISSED.ordinal(), null);
			super.processActions(list);
			super.setTxSuccessful();
			super.endTx();

			Log.i(TAG, count+" entries added.");
			//step 3 schedule alarm intent
			scheduleAlarmIntent();
		}

		/**
		 * Get the next alarm to go off
		 * @return
		 */
		public AlarmRow getNextAlarmFuture() {
			ArrayList<ContentValues> res = super.query(null,
                    AlarmDataProvider.STATE +" IN (" + ALARM_STATE.PENDING.ordinal() +
                    ", " + ALARM_STATE.SNOOZED.ordinal() + " )",
					null,
					AlarmDataProvider.TIME_TO_FIRE+" ASC LIMIT 1");
			if (res.isEmpty()) {
			    return null;
			}
			AlarmRow result = AlarmRow.fromContentValues(res.get(0));
			if ( Constants.debugAlarms && Constants.LOG_DEBUG) Log.d(TAG,
			        "getNextAlarm found: "+result.toString());
			return result;
		}

		/**
		 * Get the next alarm that is overdue or null. If null, then schedule next alarm intent.
		 * @return
		 */
		public AlarmRow getNextAlarmPast() {
			ArrayList<ContentValues> res = super.query(null,
					AlarmDataProvider.STATE +" IN (" + ALARM_STATE.PENDING.ordinal() +
						", " + ALARM_STATE.SNOOZED.ordinal() + ") AND "+
						AlarmDataProvider.BASE_TIME_TO_FIRE+" <= " + System.currentTimeMillis(),
					null,
					AlarmDataProvider.TIME_TO_FIRE+" ASC LIMIT 1");
			if (res.isEmpty()) {
				this.scheduleAlarmIntent();
				return null;
			}
			return AlarmRow.fromContentValues(res.get(0));
		}

		/**
		 * Snooze/Dismiss a specific alarm
		 * @param alarm
		 */
		public void updateAlarmState(AlarmRow row, ALARM_STATE state) {

			super.beginTx();

			//first remove any old alarms
			super.delete( AlarmDataProvider.TIME_TO_FIRE+" < "+(System.currentTimeMillis() - (86400000)), null);

			row.setState(state);
			if ( state == ALARM_STATE.SNOOZED ) row.addSnooze();
			if ( 0 < super.update(row.toContentValues(), AlarmDataProvider._ID+" = " + row.getId(), null) )
				super.setTxSuccessful();

			super.endTx();

			//Reschedule next intent.
			scheduleAlarmIntent();
		}


		/**
		 * Schedule the next alarm intent - Should be called whenever there is a change to the db.
		 * Uses setAlarmClock() for exact timing and status bar visibility.
		 */
		public void scheduleAlarmIntent() {
			AlarmRow next = getNextAlarmFuture();
			if (next == null) {
	            if ( Constants.LOG_DEBUG && Constants.debugAlarms )
	                Log.i(TAG, "No alarms scheduled.");
			    return; //nothing to schedule.
			}
			long ttf = next.getTimeToFire();
			AcalDateTime ttfHuman = AcalDateTime.getInstance().setMillis(ttf);
			Log.i(TAG, "Scheduling Alarm wakeup for "+ ((ttf - System.currentTimeMillis())/1000)+" seconds from now at "+ttfHuman.toString());
			Intent intent = new Intent(context, AlarmActivity.class);
			PendingIntent alarmIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

			// Use setAlarmClock for exact alarm timing - works reliably even in Doze mode
			// and shows the alarm in the status bar
			AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(ttf, alarmIntent);
			alarmManager.setAlarmClock(alarmClockInfo, alarmIntent);
		}

		public void logAlarmQueue() {
		    Log.i(TAG,"Alarm queue at "+AcalDateTime.getInstance().toString());
            for ( ContentValues cv :  super.query(null, null, null, AlarmDataProvider.TIME_TO_FIRE+" ASC") ) {
                try {
                    Log.i(TAG,AlarmRow.fromContentValues(cv).toString());
                }
                catch ( Exception e ) {
                    Log.e(TAG,"Error processing alarm queue",e);
                }
            }
		}

		//Deal with resource changes
		public void processChanges(ArrayList<DataChangeEvent> changes) {

			super.beginTx();
			try {
				for (DataChangeEvent change : changes) {
					switch (change.action) {
						case INSERT:
						case UPDATE:
						case PENDING_RESOURCE:
							populateTableFromResource(change.getData());
							break;
						default: break;
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Error processing resource changes: "+e+"\n"+Log.getStackTraceString(e));
			}
			super.setTxSuccessful();
			super.endTx();

			if ( Constants.LOG_DEBUG && Constants.debugAlarms ) logAlarmQueue();

			//schedule alarm intent
			scheduleAlarmIntent();
		}

		private void populateTableFromResource(ContentValues data) {
			if ( data == null
					|| VComponent.VCARD.equalsIgnoreCase(data.getAsString(ResourceTableManager.EFFECTIVE_TYPE))
					|| ( data.getAsString(ResourceTableManager.RESOURCE_DATA) == null
						&&  data.getAsString(ResourceTableManager.NEW_DATA) == null )
					) return;

            super.delete(
                    AlarmDataProvider.RESOURCE_ID+" = "+ data.getAsLong(ResourceTableManager.RESOURCE_ID)+
                    " AND "+AlarmDataProvider.STATE+" = "+ALARM_STATE.PENDING.ordinal(),
                    null);

            ArrayList<ContentValues> existing = super.query(null,
                    AlarmDataProvider.RESOURCE_ID+" = "+ data.getAsLong(ResourceTableManager.RESOURCE_ID)+
                    " AND "+AlarmDataProvider.STATE+" != "+ALARM_STATE.PENDING.ordinal(),
                    null, null);
            AlarmRow inProgressAlarm = null;
            if ( !existing.isEmpty() ) inProgressAlarm.fromContentValues(existing.get(0));

			//default start timestamp
			AcalDateTime after = new AcalDateTime().applyLocalTimeZone();

			ArrayList<AlarmRow> alarmList = new ArrayList<AlarmRow>();
			//use last dismissed to calculate start
			ArrayList<ContentValues> cvs = super.query(
			        null,
			        AlarmDataProvider.STATE+" = "+ALARM_STATE.DISMISSED.ordinal(),
			        null,
			        AlarmDataProvider.TIME_TO_FIRE+" DESC");
			if (!cvs.isEmpty()) {
			    AcalDateTime lastDismissed = AcalDateTime.fromMillis(cvs.get(0).getAsLong(AlarmDataProvider.TIME_TO_FIRE));
			    if ( lastDismissed.clone().addSeconds(30).after(after) )
			        after = lastDismissed;
			}

			Resource r = Resource.fromContentValues(data);
			VCalendar vc;
			try {
				vc = (VCalendar) VComponent.createComponentFromResource(r);
			}
			catch ( ClassCastException e ) {
				return;
			}
			catch ( VComponentCreationException e ) {
				// @todo Auto-generated catch block
				Log.w(TAG,"Auto-generated catch block", e);
				return;
			}
			if ( vc == null ) {
				Log.w(TAG,"Couldn't create VCalendar from resource "+r.getResourceId()+":\n"+r.getBlob());
				return;
			}
			vc.appendAlarmInstancesBetween(alarmList, alarmDateRange(after));

			Collections.sort(alarmList);

			//Create query List
			DMQueryList list = new DMQueryList();

            ContentValues newInstance;
            DMAction queryAction;
			for (AlarmRow alarm : alarmList) {
                newInstance = alarm.toContentValues();
                if ( inProgressAlarm != null && inProgressAlarm.equals(alarm) ) {
                    // Skip updating if the alarm is already in progress.
                    if ( alarm.getTimeToFire() < System.currentTimeMillis() ) continue;

                    queryAction = new DMUpdateQuery(newInstance, AlarmDataProvider._ID+"="+inProgressAlarm.getId(), null);
                }
                else {
                    queryAction = new DMInsertQuery( null, newInstance );
                }
				list.addAction(queryAction);
			}

			super.processActions(list);
		}

	}

    public static AcalDateRange alarmDateRange(AcalDateTime after) {
        return new AcalDateRange(after, AcalDateTime.addDays(after, 21));
    }

    public static void logCurrentAlarms(Context c) {
        getInstance(c).ATMinstance.logAlarmQueue();
    }

    public static void rebuildAlarmQueue(Context c) {
        AlarmQueueManager AQM = getInstance(c);
        AQM.ATMinstance.delete(null, null);
        AQM.rebuild();
    }
}
