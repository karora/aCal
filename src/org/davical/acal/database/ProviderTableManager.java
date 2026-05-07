package org.davical.acal.database;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.util.Log;

import org.davical.acal.Constants;


/**
 * Some useful code for DB users. Extend this and database state can be maintained.
 * call beginQuery before starting any internal queries and end query when finished any internal querys.
 * transactions are exposed and maintained.
 * 
 * @author Andrew McMillan
 *
 */
public abstract class ProviderTableManager implements TableManager {

	protected boolean inTx = false;

	private ArrayList<DataChangeEvent> changesProcessed;
	
//	private int initialPriority;
//	private static final int preferredPriority = Process.THREAD_PRIORITY_DISPLAY + (2*Process.THREAD_PRIORITY_LESS_FAVORABLE);

	protected Context context;
	protected ContentResolver mResolver;

	protected abstract Uri getCallUri();
    protected abstract Uri getQueryUri();

	
	private static final String TAG = "aCal ProviderManager";

	public abstract void dataChanged(ArrayList<DataChangeEvent> changes);
	
	protected ProviderTableManager(Context context) {
		this.context = context;
		mResolver = context.getContentResolver();
	}

	private String openStackTraceInfo = null;
	
	protected void saveStackTraceInfo() {
		int base = 3;
		int depth = 12;
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		openStackTraceInfo = "\t"+stack[base].toString();
		for (int i = base+1; i < stack.length && i< base+depth; i++)
			openStackTraceInfo += "\n\t\t"+stack[i].toString(); 
	}
	
	protected void printStackTraceInfo(int logLevel) {
			int base = 3;
			int depth = 10;
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			String info = "\t"+stack[base].toString();
			for (int i = base+1; i < stack.length && i< base+depth; i++)
				info += "\n\t\t"+stack[i].toString(); 
			Log.println(logLevel, TAG, info);
			if ( openStackTraceInfo != null ) {
				Log.println(logLevel, TAG,"  Database was opened here:\n"+openStackTraceInfo);
			}
	}

	protected ProviderTableManager startChangeSet() {
	    changesProcessed = new ArrayList<DataChangeEvent>();
	    return this;
	}

	protected ProviderTableManager addChange(DataChangeEvent e) {
		if (changesProcessed != null) changesProcessed.add(e);
        return this;
	}

	protected ArrayList<DataChangeEvent> finaliseChangeSet() {
	    ArrayList<DataChangeEvent> result = changesProcessed;
	    changesProcessed = null;
	    return result;
	}
	
	public synchronized void beginTx() {
		if ( inTx) throw new IllegalStateException("Tried to start Tx when already in TX");
		inTx = true;
        mResolver.update(Uri.withAppendedPath(getQueryUri(), "begin"), null, null, null);
	}

	public void setTxSuccessful() {
		if ( !inTx ) throw new IllegalStateException("Tried to set Tx Successful when not in TX");
        mResolver.update(Uri.withAppendedPath(getQueryUri(), "approve"), null, null, null);
	}

	public synchronized void endTx() {
		if (!inTx)  throw new IllegalStateException("Tried to end Tx when not in TX");
        mResolver.update(Uri.withAppendedPath(getQueryUri(), "commit"), null, null, null);
		inTx = false;
	}

	protected boolean doWeNeedATransaction() {
		if ( !inTx ) {
			beginTx();
			return true;
		}
		return false;
	}
	
	//Some useful generic methods

	public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs, String orderBy) {
	    Uri uri = getQueryUri();
		ArrayList<ContentValues> result = new ArrayList<ContentValues>();
		int count = 0;
		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"DB: "+uri+" query:");
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) printStackTraceInfo(Constants.LOGV);
	
		Cursor c = mResolver.query(uri, columns, selection, selectionArgs, orderBy);
		try {
			if (c.getCount() > 0) {
				for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
					result.add(new ContentValues());
					DatabaseUtils.cursorRowToContentValues(c, result.get(count++));
				}
			}
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if ( c != null ) c.close();
			while ( c != null ) {
				if (c.isClosed()) c = null;
				else
					try { Thread.sleep(10); } catch (Exception e) {}
			}
		}
		
		return result;
	}

	
	public int delete(String whereClause, String[] whereArgs) {
        Uri uri = getQueryUri();

		if (Constants.debugDatabaseManager && Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,
				"Deleting Row on "+uri+":\n\tWhere: "+whereClause);

		int count = 0;
		try {
			//First select or the row id's
			ArrayList<ContentValues> rows = this.query( null, whereClause, whereArgs, null);
			count = mResolver.delete(uri, whereClause, whereArgs);
	
			if (count != rows.size()) {
				if (Constants.debugDatabaseManager) Log.w(TAG, "Inconsistent number of rows deleted!");
			}
			if (count == 0) {
				if (Constants.debugDatabaseManager) Log.w(TAG, "No rows deleted for '"+whereClause+"' args: "+whereArgs);
			}
			else {
				for (ContentValues cv : rows) {
					addChange(new DataChangeEvent(QUERY_ACTION.DELETE,cv));
				}
			}
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		return count;
	}

	public int update(ContentValues values, String whereClause, String[] whereArgs) {
        Uri uri = getQueryUri();
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) Log.println(Constants.LOGV,TAG,
				"Updating Row on "+uri+":\n\t"+values.toString());

		int count = 0;
		try {
			count = mResolver.update(uri, values, whereClause, whereArgs);
			addChange(new DataChangeEvent(QUERY_ACTION.UPDATE, new ContentValues(values)));
		}
		catch( Exception e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		return count;
	}

	public long insert(ContentValues values) {
        Uri uri = getQueryUri();
		if (Constants.debugDatabaseManager && Constants.LOG_VERBOSE) Log.println(Constants.LOGV, TAG, 
				"Inserting Row on "+uri+":\n\t"+values.toString());

		Uri newUri = mResolver.insert(uri, values);
		long newId = Long.parseLong(newUri.getLastPathSegment());
		values.put("_id", newId);
		addChange(new DataChangeEvent(QUERY_ACTION.INSERT, new ContentValues(values)));

		return newId;
	}

	
	public boolean processActions(DMQueryList queryList) {
		boolean transactionInternally = doWeNeedATransaction();
		boolean res = false;
		try {
		    startChangeSet();
			for (DMAction action : queryList.getActions()) {
				action.process(this);
			}
			res = !changesProcessed.isEmpty();
			finaliseChangeSet();
		}
		catch ( Exception e ) {
			Log.e(TAG, "Exception processing request: " + e + Log.getStackTraceString(e));
		}
		finally {
			if ( transactionInternally ) {
				if ( res ) setTxSuccessful();
				endTx();
			}
		}
		return res;
	}
}
