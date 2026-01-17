package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.os.ConditionVariable;
import android.util.Log;
import android.widget.Toast;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.CacheModifier;
import com.morphoss.acal.database.CacheWindow;
import com.morphoss.acal.database.DMDeleteQuery;
import com.morphoss.acal.database.DMQueryBuilder;
import com.morphoss.acal.database.DMQueryList;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.TableManager.QUERY_ACTION;
import com.morphoss.acal.database.cachemanager.requests.CRAddRangeResult;
import com.morphoss.acal.database.cachemanager.requests.CRObjectsInRange;
import com.morphoss.acal.database.cachemanager.requests.CRReduceRangeSize;
import com.morphoss.acal.database.cachemanager.requests.CRResourceChanged;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requests.RRGetCacheEventsInRange;
import com.morphoss.acal.database.resourcesmanager.requests.RRGetCacheEventsInRange.RREventsInRangeResponse;
import com.morphoss.acal.dataservice.Resource;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.desktop.ShowUpcomingWidgetProvider;
import com.morphoss.acal.di.ServiceRegistry;
import com.morphoss.acal.providers.CacheDataProvider;

/**
 *
 * This class provides an interface for things that want access to the cache.
 *
 * Call the static getInstance method to get an instance of this Manager.
 *
 * To use, call sendRequest
 *
 * WARNING: Only the worker thread should access the DB directly. Everything else MUST create a request and put it on the queue
 * adding methods that directly access the db in this enclosing class could lead to race conditions and cause the db or the cache
 * to enter an inconsistent state.
 *
 * @author Chris Noldus
 *
 */
public class CacheManager implements Runnable, ResourceChangedListener, ResourceResponseListener<ArrayList<Resource>>, CacheModifier, ICacheManager, CacheTableManager.CacheManagerCallback {

	//The current instance
	private static CacheManager instance = null;
	public static final String TAG = "aCal CacheManager";

	//Settings
	private static final int DEF_MONTHS_BEFORE = -1;	//these 2 represent the default window size
	private static final int DEF_MONTHS_AFTER = 3;		//relative to todays date
	public static final boolean	DEBUG	= true && Constants.DEBUG_MODE;

	//Cache Window settings
	private final long lookForward = 86400000L*7L*10L;	//10 weeks
	private final long lookBack = 86400000L*7L*5L;	//5 week
	private final long maxSize = 86400000L*7L*26L;	//26 weeks
	private final long minPaddingForward = 86400000L*7L*5L;	//5 weeks
	private final long minPaddingBack = 86400000L*7L*5L;	//5 weeks
	private final long increment = 86400000L*7L*5L;	//5 weeks


	private final Context context;

	private CacheWindow window;

	//ThreadManagement
	private final ConditionVariable threadHolder = new ConditionVariable();
	private Thread workerThread;
	private boolean running = true;
	private final ConcurrentLinkedQueue<CacheRequest> queue = new ConcurrentLinkedQueue<CacheRequest>();
	private static final long	MAX_BLOCKING_REQUEST_WAIT	= 20000;

	//DB Constants
	private static final String FIELD_ID = "_id";
	private static final String FIELD_START = "dtstart";
	private static final String FIELD_END = "dtend";
	private static final String FIELD_COUNT = "count";
	private static final String FIELD_CLOSED = "closed";

	private static final int CLOSED_STATE_DIRTY = 0;
    private static final int CLOSED_STATE_CLEAN = 1;

	//Comms
	private final CopyOnWriteArraySet<CacheChangedListener> listeners = new CopyOnWriteArraySet<CacheChangedListener>();
	private ResourceManager rm;

	//Request Processor Instance
	private CacheTableManager CTMinstance;

	private static Semaphore lockSem = new Semaphore(1, true);
	private static volatile boolean lockdb = false;


	//Get an instance
	public synchronized static CacheManager getInstance(Context context) {
		if (instance == null) instance = new CacheManager(context);
		return instance;
	}

	//get and instance and add a callback handler to receive notfications of change
	//It is vital that classes remove their handlers when terminating
	public synchronized static CacheManager getInstance(Context context, CacheChangedListener listener) {
		if (instance == null) {
			instance = new CacheManager(context);
			instance.checkDefaultWindow();
		}
		instance.addListener(listener);
		return instance;
	}

	private CacheTableManager getCTMInstance() {
		if (CTMinstance == null) CTMinstance = new CacheTableManager(context, this, listeners);
		return CTMinstance;
	}

	// CacheManagerCallback implementation
	@Override
	public CacheWindow getWindow() {
		return window;
	}

	@Override
	public void setWindow(CacheWindow newWindow) {
		this.window = newWindow;
	}

	@Override
	public long getLookForward() {
		return lookForward;
	}

	@Override
	public long getLookBack() {
		return lookBack;
	}

	@Override
	public long getMaxSize() {
		return maxSize;
	}

	@Override
	public long getMinPaddingBack() {
		return minPaddingBack;
	}

	@Override
	public long getMinPaddingForward() {
		return minPaddingForward;
	}

	@Override
	public long getIncrement() {
		return increment;
	}

	@Override
	public int getDefMonthsBefore() {
		return DEF_MONTHS_BEFORE;
	}

	@Override
	public int getDefMonthsAfter() {
		return DEF_MONTHS_AFTER;
	}

	/**
	 * CacheManager needs a context to manage the DB. Should run under AcalService.
	 * Loadstate ensures that our DB is consistent and should be run before any resource
	 * modifications can be made by any other part of the system.
	 */
	private CacheManager(Context context) {
		this.context = context;

        this.CTMinstance = this.getCTMInstance();
        rm = ResourceManager.getInstance(context);

		loadState();
		workerThread = new Thread(this);
		workerThread.start();

		// Register with ServiceRegistry for DI
		ServiceRegistry.register(ICacheManager.class, this);
	}

	@Override
	public void checkDefaultWindow() {
		//ensure window contains the minimum range
		AcalDateTime st  = new AcalDateTime();
		st.setDaySecond(0).setMonthDay(1);
		AcalDateTime en = st.clone();
		st.addMonths(DEF_MONTHS_BEFORE);
		en.addMonths(DEF_MONTHS_AFTER);
		this.sendRequest(new CRObjectsInRange(new AcalDateRange(st,en),null));
	}


	/**
	 * Add a lister to change events. Change events are fired whenever a change to the DB occurs
	 * @param ccl
	 */
	public void addListener(CacheChangedListener ccl) {
		synchronized (listeners) {
			this.listeners.add(ccl);
		}
	}

	/**
	 * Remove an existing listener. Listeners should always be removed when they no longer require changes.
	 * @param ccl
	 */
	public void removeListener(CacheChangedListener ccl) {
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
            Log.e(TAG,"Unable to acquire metalock.", new Exception("Stack Trace"));
        }
		if (lockdb) throw new IllegalStateException("Cant acquire a lock that hasnt been released!");
		lockdb = true;
		lockSem.release();
	}

	private synchronized static void releaseMetaLock() {
        if (!lockdb) Log.e(TAG,"Lock released already!", new Exception("Stack Trace"));
//		if (!lockdb) throw new IllegalStateException("Cant release a lock that hasnt been obtained!");
		lockdb = false;
	}

    public synchronized static void setResourceInTx(Context c, boolean inTxn) {
		ContentValues data = new ContentValues();
		ContentResolver cr = c.getContentResolver();

		AcalDateRange currentRange = null;
		try { currentRange = instance.window.getCurrentWindow(); } catch( Exception e ) { };
		if (currentRange == null) currentRange = new AcalDateRange(new AcalDateTime(), new AcalDateTime());

		acquireMetaLock();

		//get current values
		Cursor cursor = null;
		try {
			cursor = cr.query(CacheDataProvider.META_URI, null, null, null, null);
			if (cursor.getCount() >= 1) {
				cursor.moveToFirst();
				DatabaseUtils.cursorRowToContentValues(cursor, data);
				cursor.close();
				cursor = null;
				data.put(FIELD_CLOSED, (inTxn ? CLOSED_STATE_DIRTY : CLOSED_STATE_CLEAN));
				if (!inTxn && instance != null) {
					data.put(FIELD_START, currentRange.start.getMillis());
					data.put(FIELD_END, currentRange.end.getMillis());
				}
				cr.update(CacheDataProvider.META_URI, data, FIELD_ID+" = ?", new String[]{data.getAsLong(FIELD_ID)+""});
			}
			else {
				data = new ContentValues();
				data.put(FIELD_CLOSED, (inTxn ? CLOSED_STATE_DIRTY : CLOSED_STATE_CLEAN));
				data.put(FIELD_COUNT, 0);
				if (!inTxn && instance != null) {
					data.put(FIELD_START, currentRange.start.getMillis());
					data.put(FIELD_END, currentRange.end.getMillis());
				}
				else {
					long now = System.currentTimeMillis();
					data.put(FIELD_START, now);
					data.put(FIELD_END, now);
				}
				cr.insert(CacheDataProvider.META_URI, data);
			}

		}
		catch( Exception e ) {
			Log.i(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( cursor != null && !cursor.isClosed()) cursor.close();
			releaseMetaLock();

		}
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
		saveState();
		// Unregister from ServiceRegistry
		ServiceRegistry.unregister(ICacheManager.class);
		workerThread = null;
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
		AcalDateRange windowRange = window.getCurrentWindow();
		if (windowRange != null) {
			data.put(FIELD_START, windowRange.start.getMillis());
			data.put(FIELD_END, windowRange.end.getMillis());
		} else {
			data.put(FIELD_START, -1);
			data.put(FIELD_END, -1);
		}

		// set CLOSED to true
		cr.update(CacheDataProvider.META_URI, data, null, null);

		//dereference ourself so GC can clean up
		instance = null;
		this.CTMinstance = null;
		rm.removeListener(this);
		rm = null;
		releaseMetaLock();
	}

	/**
	 * Called on start up. if safe==false flush cache. set safe to false regardless.
	 */
	private void loadState() {
        ContentResolver cr = context.getContentResolver();
        ContentValues data = new ContentValues();
        long start;
        long end;
        AcalDateRange range = null;

        acquireMetaLock();

		//load start/end range from meta table
		AcalDateTime defaultWindow = new AcalDateTime();
		Cursor mCursor = null;
		try {
			mCursor = cr.query(CacheDataProvider.META_URI, null, null, null, null);
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
				data.put(FIELD_CLOSED, CLOSED_STATE_DIRTY);
				data.put(FIELD_COUNT, 0);
				data.put(FIELD_START,  defaultWindow.getMillis());
				data.put(FIELD_END,  defaultWindow.getMillis());
			} else  {
				mCursor.moveToFirst();
				DatabaseUtils.cursorRowToContentValues(mCursor, data);
			}
			closedState = data.getAsInteger(FIELD_CLOSED);
            start = data.getAsLong(FIELD_START);
            end = data.getAsLong(FIELD_END);
            if (start >= 0 && end >= 0 ) range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
		}
		catch( Exception e ) {
			Log.i(TAG,Log.getStackTraceString(e));
            data.put(FIELD_CLOSED, CLOSED_STATE_DIRTY);
            data.put(FIELD_COUNT, 0);
            start = defaultWindow.getMillis();
            end = defaultWindow.getMillis();
            data.put(FIELD_START,  start );
            data.put(FIELD_END, end );
            range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
		}
		finally {
			if ( mCursor != null ) mCursor.close();
		}

		if ( closedState == CLOSED_STATE_DIRTY ) {
			Log.println(Constants.LOGI,TAG, "Application not closed correctly last time. Resetting cache.");
			Toast.makeText(context, "Rebuilding cache - It may take some time before events are visible.",Toast.LENGTH_LONG).show();
			this.CTMinstance.clearCache();
			data.put(FIELD_COUNT, 0);
            start = defaultWindow.getMillis();
            end = defaultWindow.getMillis();
			data.put(FIELD_START,  start );
			data.put(FIELD_END, end );
            range = new AcalDateRange(AcalDateTime.fromMillis(start), AcalDateTime.fromMillis(end));
		}
		data.put(FIELD_CLOSED, CLOSED_STATE_CLEAN);
		cr.delete(CacheDataProvider.META_URI, null, null);

		data.remove(FIELD_ID);
		cr.insert(CacheDataProvider.META_URI, data);

		window = new CacheWindow(lookForward, lookBack, maxSize, minPaddingBack,
								minPaddingForward, increment, this, new AcalDateTime());
		if (range != null) window.setWindowSize(range);

		rm.addListener(this);
		releaseMetaLock();

	}


	/**
	 * This is called by the cache window when we should reduce our size.
	 */
	@Override
	synchronized public void deleteRange(AcalDateRange range) {
	    Log.i(TAG,"Requesting deletion of cache range: "+range.toString());
		this.sendRequest(new CRReduceRangeSize(range));
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
				CacheRequest request = queue.poll();
				CTMinstance.process(request);
			}
//			if (!CacheManager.resourceInTransaction) setDBisDirty(context,false);
			//Wait till next time
			threadHolder.close();
			threadHolder.block();
		}
	}

	/**
	 * Send a request to the CacheManager. Requests are queued and processed asynchronously. No guarantee is given as to
	 * the order of processing, so if sending multiple requests, consider potential race conditions.
	 * @param request
	 * @throws IllegalStateException thrown if close() has been called.
	 */
	public void sendRequest(CacheRequest request) throws IllegalStateException {
		if (instance == null || this.workerThread == null || this.CTMinstance == null)
			throw new IllegalStateException("CM in illegal state - probably because sendRequest was called after close() has been called.");
		queue.offer(request);
		threadHolder.open();
	}

	/**
	 * Send a request to the CacheManager. thread is blocked until response is generated. No guarantee is given as to
	 * the order of processing, so if sending multiple requests, consider potential race conditions.
	 * @param request
	 * @throws IllegalStateException thrown if close() has been called.
	 */
	public <E> CacheResponse<E> sendRequest(BlockingCacheRequestWithResponse<E> request) throws IllegalStateException {
		if (instance == null || this.workerThread == null || this.CTMinstance == null)
			throw new IllegalStateException("CM in illegal state - probably because sendRequest was called after close() has been called.");
		queue.offer(request);
		threadHolder.open();
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		long stopWaiting = System.currentTimeMillis() + MAX_BLOCKING_REQUEST_WAIT;
		while ( !request.isProcessed() ) {
			try { Thread.sleep(10); } catch (Exception e) {	}
			if ( System.currentTimeMillis() > stopWaiting )
				throw new IllegalStateException("Waited too long ("+MAX_BLOCKING_REQUEST_WAIT/1000+"s) for "+request.getClass().getSimpleName()+" response!");
		}
		Thread.currentThread().setPriority(priority);
		return request.getResponse();
	}

	//Request events (FROM RESOURCES) that
	@Override
	public void retrieveRange() {
		if (window.getRequestedWindow() == null) return;
		if ( DEBUG && Constants.LOG_DEBUG ) Log.println(Constants.LOGD,TAG,"Sending RRGetCacheEventsInRange Request");
		ResourceManager.getInstance(context).sendRequest(new RRGetCacheEventsInRange(window, this));
	}

	@Override
	public void resourceResponse(ResourceResponse<ArrayList<Resource>> response) {
		if (response instanceof RREventsInRangeResponse<?>) {
			RREventsInRangeResponse<ArrayList<Resource>> res = (RREventsInRangeResponse<ArrayList<Resource>>) response;
			//calculate events

			AcalDateRange range = window.getRequestedWindow();
			if (range == null) {
				Log.w(TAG, "Have response from range request but window says no range requested? Aborting!", new Exception(""));
				return;
			}


			ArrayList<CacheObject> events = new ArrayList<CacheObject>();
			//step 3 - foreach resource, Vcomps
			//This is very CPU intensive, so lower our priority to prevent interfering with other parts of the app.
			int currentPri = Thread.currentThread().getPriority();
			Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			for (Resource r : res.result()) {
				try {
				//if VComp is VCalendar
				VComponent comp = VComponent.createComponentFromResource(r);
				if (comp instanceof VCalendar)
					((VCalendar)comp).appendCacheEventInstancesBetween(events, range);
				} catch (VComponentCreationException e) {
					//not a vcal

				}

			}
			Thread.currentThread().setPriority(currentPri);
			if ( DEBUG && Constants.LOG_DEBUG )
				Log.println(Constants.LOGD,TAG,events.size()+"Event Instances obtained. Posting Response.");


			//put new data on the process queue

			DMQueryList inserts = new DMQueryList();

			if ( DEBUG && Constants.LOG_DEBUG )
				Log.println(Constants.LOGD,TAG, "Have response from Resource manager for range request.");
			//We should have exclusive DB access at this point
			if ( DEBUG && Constants.LOG_DEBUG )
				Log.println(Constants.LOGD,TAG, "Queueing delete of events in "+range);
			inserts.addAction(new DMQueryBuilder()
							.setAction(QUERY_ACTION.DELETE)
							.setWhereClause(FIELD_START+" >= ? AND "+FIELD_START+" <= ?")
							.setwhereArgs(new String[]{range.start.getMillis()+"", range.end.getMillis()+""})
							.build());

			if ( DEBUG && Constants.LOG_DEBUG )
				Log.println(Constants.LOGD,TAG, "Queueing insert of "+events.size()+" new events in "+range);
			for (CacheObject event : events) {
				if ( event.getStart() == Long.MAX_VALUE || event.getEnd() == Long.MAX_VALUE ) {
					// Single instance tasks with a null start date can get included multiple times
					inserts.addAction(new DMDeleteQuery(
							CacheTableManager.FIELD_RESOURCE_ID+"="+event.getResourceId() +
							" AND "+CacheTableManager.FIELD_DTSTART+"="+event.getStart() +
							" AND "+CacheTableManager.FIELD_DTEND+"="+event.getEnd()
							, null));
				}
				ContentValues toInsert = event.getCacheCVs();
				inserts.addAction(new DMQueryBuilder().setAction(QUERY_ACTION.INSERT).setValues(toInsert).build());
			}

			this.sendRequest(new CRAddRangeResult(inserts, range));
		}
	}


	/**
	 * Constructs the WHERE clause for a date range of cache rows
	 * @param range
	 * @param cacheObjectType
	 * @return The WHERE clause
	 */
    public static String whereClauseForRange(AcalDateRange range, String cacheObjectType) {
        long dtStart = range.start.getMillis();
        long dtEnd = range.end.getMillis();
        int offsetS = TimeZone.getDefault().getOffset(range.start.getMillis());
        int offsetE = TimeZone.getDefault().getOffset(range.start.getMillis());

        return
        "( " +
            "( "+CacheTableManager.FIELD_DTEND+" > "+dtStart+" AND NOT "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
                " OR "+
            "( "+CacheTableManager.FIELD_DTEND+" - "+offsetS+" > "+dtStart+" AND "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
                " OR "+
            "( "+CacheTableManager.FIELD_DTEND+" ISNULL )"+
        " ) AND ( "+
            "( "+CacheTableManager.FIELD_DTSTART+" < "+dtEnd+" AND NOT "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
                " OR "+
            "( "+CacheTableManager.FIELD_DTSTART+" - "+offsetE+" < "+dtEnd+" AND "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
                " OR "+
            "( "+CacheTableManager.FIELD_DTSTART+" ISNULL )"+
        ")" +
        ( cacheObjectType == null ? "" : " AND "+CacheTableManager.FIELD_RESOURCE_TYPE+"='"+cacheObjectType+"'") +
        " AND dav_server.active = 1 " +
        " AND (dav_collection.active_events = 1 OR dav_collection.active_tasks = 1 OR dav_collection.active_journal = 1)"+
        " AND "+CacheTableManager.FIELD_RESOURCE_ID + " IS NOT NULL";
    }





	/**
	 * This method should only ever be called from within the dataChanged method of ResourceTableManager.
	 * Because of this, we can take into account some simple possibilities:
	 *
	 * FACT: This method was caused by the workerThread of ResourceManager executing a ResourceRequest
	 * FACT: If it was not one of our requests that caused the change, then any requests of ours that have
	 * 		not yet been processed will include this updated information and overwriting is O.K.
	 * FACT: If it was one of our requests that caused the change, then we will get a response only after
	 * 		this method has finished processing, and the response will also have only current information.
	 * FACT: Any of our requests that were processed BEFORE these changes have taken affect have either been
	 * 		dealt with or are in our QUEUE
	 *
	 *  Conclusion: As long as any work that needs to be done is added to our queue, our state should remain consistent.
	 *
	 */
	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		//this processing can be fairly heavy duty, so we lower our thread priority to keep
		//gui responsive
		int priority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		ArrayList<DataChangeEvent> changes = event.getChanges();
		if (changes == null || changes.isEmpty()) return;	//dont care

		AcalDateRange windowRange = window.getCurrentWindow();
		if (windowRange == null) return; // dont care
		DMQueryList queries = new DMQueryList();
		Resource r;
		VComponent comp;
		ArrayList<CacheObject> newData;
		for (DataChangeEvent change : changes) {
			if ( change.action == null ) continue;
			switch ( change.action ) {
				case INSERT:
				case PENDING_RESOURCE:
				case UPDATE:
					r = event.getResource(change);
					// If this resource in our window?

					// Construct resource
					try {
						if ( r == null ) continue;
						comp = VComponent.createComponentFromResource(r);
						if ( comp == null ) continue;
						// get instances within window

						if ( DEBUG && Constants.LOG_DEBUG ) {
							Log.println(Constants.LOGD, TAG,
									"Processing a resource changed for a " + comp.getEffectiveType() +
									" Collection/ResourceID: "+r.getCollectionId()+"/"+r.getResourceId()+", earliest: "+r.getEarliestStart()+", latest: "+r.getLatestEnd());
						}

						newData = new ArrayList<CacheObject>();
						if ( comp instanceof VCalendar ) {
							((VCalendar) comp).appendCacheEventInstancesBetween(newData, windowRange);

							// Delete existing first
							queries.addAction(new DMDeleteQuery(CacheTableManager.FIELD_RESOURCE_ID+"="+r.getResourceId(), null));

							// Then add all instances
							for (CacheObject co : newData) {
							    if ( co.getCollectionId() < 1 ) throw new RuntimeException("Attempt to insert into cache with invalid collection ID");
								queries.addAction(new DMQueryBuilder().setAction(QUERY_ACTION.INSERT)
										.setValues(co.getCacheCVs()).build());
							}
						}

					}
					catch ( Exception e ) {
						Log.e(TAG, "Error Handling Resoure Change.", e);
					}

					break;
				case DELETE:
					long rid = change.getData().getAsLong(ResourceManager.ResourceTableManager.RESOURCE_ID);
					queries.addAction(new DMDeleteQuery(CacheTableManager.FIELD_RESOURCE_ID+"="+rid, null));
					break;
			}
		}

		if ( !queries.isEmpty() ) {
			try {
				this.sendRequest(new CRResourceChanged(queries));
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}

		Thread.currentThread().setPriority(priority);

	}

}
