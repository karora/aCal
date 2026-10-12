/*
 * Copyright (C) 2026 Andrew McMillan
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
package org.davical.acal.activity;

import android.content.ContentValues;

import org.davical.acal.R;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.davacal.Masterable;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;

/**
 * A pending change that was refused, as the user is told about it.
 */
public class UnsavedChange {

	public enum Kind {
		NEW(R.string.unsaved_kind_new, R.string.unsaved_discard_new),
		CHANGED(R.string.unsaved_kind_changed, R.string.unsaved_discard_changed),
		DELETED(R.string.unsaved_kind_deleted, R.string.unsaved_discard_deleted);

		/** The word for this kind of change */
		public final int label;
		/** What discarding a change of this kind will do */
		public final int discardWarning;

		private Kind(int label, int discardWarning) {
			this.label = label;
			this.discardWarning = discardWarning;
		}
	}

	public final long pendingId;
	public final long collectionId;
	public final Kind kind;
	/** What the answer was when it was refused */
	public final int status;
	/** What the resource is called, or null if that cannot be told */
	public final String title;
	/** When the event or task starts, or null if it has no start or is not one */
	public final AcalDateTime start;

	public UnsavedChange(ContentValues pending) {
		String oldData = pending.getAsString(ResourceTableManager.OLD_DATA);
		String newData = pending.getAsString(ResourceTableManager.NEW_DATA);
		// What is being deleted is described by what it was
		String shown = (newData == null ? oldData : newData);
		Integer answer = pending.getAsInteger(ResourceTableManager.PEND_LAST_STATUS);

		pendingId = pending.getAsLong(ResourceTableManager.PENDING_ID);
		collectionId = pending.getAsLong(ResourceTableManager.PEND_COLLECTION_ID);
		kind = kindOf(pending.getAsString(ResourceTableManager.RESOURCE_NAME), oldData, newData);
		status = (answer == null ? 0 : answer);
		title = titleOf(shown);
		start = startOf(shown);
	}

	/**
	 * @param name the name the resource has on the server, null if it has never had one
	 */
	static Kind kindOf(String name, String oldData, String newData) {
		if ( newData == null ) return Kind.DELETED;
		if ( name == null || name.equals("null") || oldData == null ) return Kind.NEW;
		return Kind.CHANGED;
	}

	/**
	 * @return the string that says why a change of this kind got this answer
	 */
	static int reasonFor(Kind kind, int status) {
		switch ( status ) {
			case 403:
				return R.string.unsaved_reason_forbidden;
			case 404:
			case 410:
				return R.string.unsaved_reason_gone;
			case 409:
			case 412:
				return (kind == Kind.NEW ? R.string.unsaved_reason_exists : R.string.unsaved_reason_conflict);
			case 413:
				return R.string.unsaved_reason_too_large;
		}
		return R.string.unsaved_reason_other;
	}

	public int reason() {
		return reasonFor(kind, status);
	}

	private static Masterable masterOf(String data) {
		if ( data == null ) return null;
		try {
			VComponent component = VComponent.createComponentFromBlob(data);
			if ( component instanceof VCalendar ) return ((VCalendar) component).getMasterChild();
		}
		catch ( Exception e ) {
			// Then we cannot say what it is, but can still say that it was refused
		}
		return null;
	}

	static String titleOf(String data) {
		if ( data == null ) return null;
		Masterable master = masterOf(data);
		if ( master != null ) {
			String summary = master.getSummary();
			return (summary == null || summary.trim().equals("") ? null : summary.trim());
		}
		// A contact has no summary, but does have a name
		for ( String line : data.split("\r?\n") ) {
			if ( line.startsWith("FN:") && line.length() > 3 ) return line.substring(3).trim();
		}
		return null;
	}

	static AcalDateTime startOf(String data) {
		Masterable master = masterOf(data);
		if ( master == null ) return null;
		try {
			return master.getStart();
		}
		catch ( Exception e ) {
			return null;
		}
	}
}
