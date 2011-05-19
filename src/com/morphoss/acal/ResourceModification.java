package com.morphoss.acal;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.davacal.VCalendar;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.PendingChanges;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;

public class ResourceModification {

	private static final String TAG = "Acal ResourceModification";
	private final WriteActions modificationAction;
	private final ContentValues resourceValues;
	private final Integer pendingId;
	private Integer resourceId = null;
	private DatabaseChangedEvent dbChangeNotification = null;
	
	public ResourceModification(WriteActions action, ContentValues inResourceValues, Integer pendingId) {
		if ( action == WriteActions.UPDATE || action == WriteActions.INSERT ) {
			VComponent vCal = null;
			try {
				vCal = VCalendar.createComponentFromResource(inResourceValues, null);
				try {
					AcalRepeatRule rRule = AcalRepeatRule.fromVCalendar((VCalendar) vCal);
					AcalDateRange instancesRange = rRule.getInstancesRange();
				
					inResourceValues.put(DavResources.EARLIEST_START, instancesRange.start.getMillis());
					if ( instancesRange.end == null )
						inResourceValues.putNull(DavResources.LATEST_END);
					else
						inResourceValues.put(DavResources.LATEST_END, instancesRange.end.getMillis());
				}
				catch ( Exception e ) {
					
				}
			}
			catch (Exception e) {
			}
		}

		this.modificationAction = action;
		this.resourceValues = inResourceValues;
		this.pendingId = pendingId;
		this.resourceId = resourceValues.getAsInteger(DavResources._ID);
	}


	/**
	 * Commit this change to the dav_resource table.
	 * @param db The database handle to use for the commit
	 */
	public void commit( SQLiteDatabase db ) {
		getResourceId();

		if ( Constants.LOG_DEBUG )
			Log.d(TAG, "Writing Resource with " + modificationAction + " on resource ID "
						+ (resourceId == null ? "new" : Integer.toString(resourceId)));

		switch (modificationAction) {
			case UPDATE:
				db.update(DavResources.DATABASE_TABLE, resourceValues, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				if (resourceValues.getAsString(DavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_UPDATED,
								DavResources.class, resourceValues);
				break;
			case INSERT:
				resourceId = (int) db.insert(DavResources.DATABASE_TABLE, null, resourceValues);
				resourceValues.put(DavResources._ID, resourceId);
				if (resourceValues.getAsString(DavResources.RESOURCE_DATA) != null)
					dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_INSERTED,
								DavResources.class, resourceValues);
				break;
			case DELETE:
				if (Constants.LOG_DEBUG)
					Log.d(TAG,"Deleting resources with ResourceId = " + Integer.toString(resourceId) );
				db.delete(DavResources.DATABASE_TABLE, DavResources._ID + " = ?",
							new String[] { Integer.toString(resourceId) });
				dbChangeNotification = new DatabaseChangedEvent( DatabaseChangedEvent.DATABASE_RECORD_DELETED,
								DavResources.class, resourceValues);
				break;
		}

		if ( pendingId != null ) {
			// We can retire this change now
			int removed = db.delete(PendingChanges.DATABASE_TABLE,
					  PendingChanges._ID+"=?",
					  new String[] { Integer.toString(pendingId) });
			if ( Constants.LOG_DEBUG )
				Log.d(TAG, "Deleted "+removed+" one pending_change record ID="+pendingId+" for resourceId="+resourceId);

			ContentValues pending = new ContentValues();
			pending.put(PendingChanges._ID, pendingId);
			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(DatabaseChangedEvent.DATABASE_RECORD_DELETED, PendingChanges.class, pending));
		}
	}

	
	public void notifyChange() {
		if ( dbChangeNotification == null ) {
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"No change to notify to databaseDispatcher");
			return;
		}
		aCalService.databaseDispatcher.dispatchEvent(dbChangeNotification);
	}

	
	public Integer getResourceId() {
		return resourceId;
	}

	
	public Integer getPendingId() {
		return pendingId;
	}

	/**
	 * Use this to apply the resource modifications to the database.  In this case you
	 * will need to commit the database transaction afterwards
	 * @param changeList The List of ResourceModification objects to be applied
	 */
	public static boolean applyChangeList(SQLiteDatabase db, List<ResourceModification> changeList) {
		boolean completed =false;
		try {

			for ( ResourceModification changeUnit : changeList ) {
				changeUnit.commit(db);
				db.yieldIfContendedSafely();
			}
			completed = true;
		}
		catch (Exception e) {
			Log.e(TAG, "Exception updating resources DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
		}
		return completed;
	}

	
	/**
	 * Use this to actually commit our resource modifications to the database.
	 * @param changeList The List of ResourceModification objects to be committed
	 */
	public static void commitChangeList(Context c, List<ResourceModification> changeList) {
		AcalDBHelper dbHelper = new AcalDBHelper(c);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.beginTransaction();

		boolean successful = applyChangeList(db,changeList); 
		if ( successful ) db.setTransactionSuccessful();

		db.endTransaction();
		db.close();
		if ( successful ) {
			DatabaseChangedEvent.beginResourceChanges();
			for ( ResourceModification changeUnit : changeList ) {
				changeUnit.notifyChange();
			}
			DatabaseChangedEvent.endResourceChanges();
		}
	}
	
}
