package org.davical.acal.database.cachemanager;

import android.content.ContentValues;

import java.util.ArrayList;

import org.davical.acal.database.DataChangeEvent;

public class CacheChangedEvent {

	private ArrayList<DataChangeEvent> changes;
	private boolean windowOnly;
	
	public CacheChangedEvent(ArrayList<DataChangeEvent> changes, boolean windowOnly) {
		this.changes = changes;
		this.windowOnly = windowOnly;
	}
	
	public boolean isWindowOnly() { return this.windowOnly; }

	/**
	 * Could a change to this row of the cache alter a list of tasks or notes
	 * which shows what falls between two times?
	 *
	 * A task or note need have no start, and no end either.  The cache holds
	 * Long.MAX_VALUE for a time that is not set, and such a row is in the list
	 * whatever its range is.
	 *
	 * @param row the row as it is, or was, in the cache
	 * @param listCompleted true if the list shows what has been completed
	 */
	public static boolean mayAffectList(ContentValues row, boolean listCompleted, long rangeStart, long rangeEnd) {
		Long completed = row.getAsLong(CacheTableManager.FIELD_COMPLETED);
		if ( !listCompleted && completed != null && completed < Long.MAX_VALUE ) return false;

		Long start = row.getAsLong(CacheTableManager.FIELD_DTSTART);
		Long end = row.getAsLong(CacheTableManager.FIELD_DTEND);
		if ( start == null || end == null || start == Long.MAX_VALUE || end == Long.MAX_VALUE ) return true;
		return start < rangeEnd && end > rangeStart;
	}
	public ArrayList<DataChangeEvent> getChanges() { return this.changes; }
}
