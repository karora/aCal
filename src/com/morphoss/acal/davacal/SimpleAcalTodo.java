/*
 * Copyright (C) 2011 Morphoss Ltd
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
package com.morphoss.acal.davacal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.R;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.activity.TodoEdit;


/**
 * 
 * @author Morphoss Ltd
 *
 */
public class SimpleAcalTodo implements Parcelable, Comparable<SimpleAcalTodo> {

	private static final String TAG = "SimpleAcalTodo"; 

	//Start and end times are in UTC
	public final int resourceId;
	public final String summary;
	public final int colour;
	public final String location;
	public final boolean hasAlarm;
	public final boolean isPending;
	public final boolean alarmEnabled;
	
	public final Long dtstart;
	public final Long due;
	public final Long duration;
	public final Long completed;
	public final Integer percentComplete;
	public final int priority;
	public final int status;
	
	final public static int TODO_STATUS_MISSING = 0;
	final public static int TODO_STATUS_NEEDS_ACTION = 1;
	final public static int TODO_STATUS_COMPLETED = 2;
	final public static int TODO_STATUS_IN_PROCESS = 3;
	final public static int TODO_STATUS_CANCELLED = 4;

	public int operation = TodoEdit.ACTION_NONE;
	
	/**
	 * Construct a new SimpleAcalEvent from all of the parameters
	 * @param start
	 * @param end
	 * @param resourceId
	 * @param summary
	 * @param location
	 * @param colour
	 * @param isAlarming
	 * @param isRepetitive
	 * @param allDayEvent
	 * @param isPending
	 */
	public SimpleAcalTodo(Long dtstart, Long due, Long duration, int resourceId, String summary, String location, int colour,
				boolean isAlarming, boolean isRepetitive, boolean allDayEvent, boolean isPending,
				boolean alarmEnabled, int priority, int percentComplete, Long completed, int status ) {
		this.dtstart = dtstart;
		this.due = due;
		this.duration = duration;
		this.completed = completed;
		this.resourceId = resourceId;
		this.summary = summary;
		this.location = location;
		this.colour = colour;
		this.hasAlarm = isAlarming;
		this.alarmEnabled = alarmEnabled;
		this.isPending = isPending;
		this.priority = (priority < 1 ? 0 : (priority > 8 ? 9 : priority));
		this.percentComplete = (percentComplete < 1 ? 0 : (percentComplete > 99 ? 100 : percentComplete));
		this.status = (percentComplete > 99 ? TODO_STATUS_COMPLETED : (status < 1 || status > 4 ? TODO_STATUS_MISSING : status ));

	}

	static public int getDateHash(int day, int month, int year) {
		return (year << 9) + (month << 5) + day;
	}
	

	/**
	 * Construct a SimpleAcalEvent from bits of VComponent, with overrides for
	 * startDate & duration so we can build it inside a repeat rule calculation 
	 * @param task The parsed VComponent which is the event
	 * @param startDate To override the startDate in the event.
	 * @param duration To override the duration or end date in the event 
	 */
	public SimpleAcalTodo(VComponent task, boolean isPending ) {
		this.resourceId = task.getResourceId();
		
		this.isPending = isPending;

		// Per RFC5545 DTSTART is optional, and one of DUE or DURATION may be present
		AcalProperty aProperty = task.getProperty("DTSTART");
		Long tmpDtstart = null;
		try {
			if ( aProperty != null )
				tmpDtstart = AcalDateTime.fromAcalProperty(aProperty).applyLocalTimeZone().getEpoch();
		}
		catch( NullPointerException e ) {};

		aProperty = task.getProperty("DUE");
		Long tmpDue = null;
		Long tmpDuration = null;
		try {
			if ( aProperty != null )
				tmpDue = AcalDateTime.fromAcalProperty(aProperty).applyLocalTimeZone().getEpoch();
		}
		catch( NullPointerException e ) {};
		if ( tmpDue == null ) {
			aProperty = task.getProperty("DURATION");
			try {
				if ( aProperty != null )
					tmpDuration = AcalDuration.fromProperty(aProperty).getDurationMillis() / 1000L;
			}
			catch( NullPointerException e ) {};

			if ( tmpDtstart != null && tmpDuration != null ) {
				tmpDue = tmpDtstart + tmpDuration;
			}
		}
		dtstart = tmpDtstart;
		due = tmpDue;
		duration = tmpDuration;
		
		aProperty = task.getProperty("COMPLETED");
		Long tmpCompleted = null;
		try {
			if ( aProperty != null )
				tmpCompleted = AcalDateTime.fromAcalProperty(aProperty).applyLocalTimeZone().getEpoch();
		}
		catch( NullPointerException e ) {};
		completed = tmpCompleted;
		
		summary = task.safePropertyValue("SUMMARY");
		location = task.safePropertyValue("LOCATION");

		aProperty = task.getProperty("PRIORITY");
		int tmpInt = 0; 
		try {
			tmpInt = (aProperty == null ? 0 : Integer.parseInt(aProperty.getValue()));
		}
		catch( Exception e ) {
		}
		priority = (tmpInt < 1 ? 0 : (tmpInt > 8 ? 9 : tmpInt));  // Ref. RFC5545, 3.8.1.9

		aProperty = task.getProperty("PERCENT-COMPLETE");
		tmpInt = 0;
		try {
			tmpInt = (aProperty == null ? 0 : Integer.parseInt(aProperty.getValue()));
		}
		catch( Exception e ) {
		}
		int tmpPercentComplete = (tmpInt > 99 || completed != null ? 100 : (tmpInt < 1 ? 0 : tmpInt));  // Ref. RFC5545, 3.8.1.8

		String tmpString = task.safePropertyValue("STATUS").toUpperCase(Locale.US);
		if ( tmpString.equals("NEEDS-ACTION") ) tmpInt = TODO_STATUS_NEEDS_ACTION;
		else if ( tmpString.equals("COMPLETED") ) tmpInt = TODO_STATUS_COMPLETED;
		else if ( tmpString.equals("IN-PROCESS") ) tmpInt = TODO_STATUS_IN_PROCESS;
		else if ( tmpString.equals("CANCELLED") ) tmpInt = TODO_STATUS_CANCELLED;
		else tmpInt = TODO_STATUS_MISSING;
		this.status = (tmpPercentComplete > 99 ? TODO_STATUS_COMPLETED : (tmpInt < 1 || tmpInt > 4 ? TODO_STATUS_MISSING : tmpInt ));

		if ( status == TODO_STATUS_COMPLETED ) {
			percentComplete = 100;
		}
		else {
			percentComplete = tmpPercentComplete;
		}

		boolean isAlarming = false;
		for( VComponent child : task.getChildren() ) {
			if ( child instanceof VAlarm ) {
				isAlarming = true;
				break;
			}
		}
		hasAlarm = isAlarming;
		
		int aColor = Color.BLUE;
		boolean alarmsForCollection = true;
		try {
			aColor = task.getCollectionColour();
			alarmsForCollection = task.getAlarmEnabled();
		} catch (Exception e) {
			Log.e(TAG,"Error Creating SimpleAcalTodo - "+e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		colour = aColor;
		alarmEnabled = alarmsForCollection;
		
	}

	
	/**
	 * Return a pretty string indicating the time period of the event.  If the start or end
	 * are on a different date to the view start/end then we also include the date on that
	 * element.  If it is an all day event, we say so. 
	 * @param as24HourTime - from the pref
	 * @return A nicely formatted string explaining the start/end of the event.
	 */
	public String getTimeText(Context c, boolean as24HourTime ) {
		if ( dtstart == null && due == null ) return c.getString(R.string.Unscheduled);
		SimpleDateFormat formatter = new SimpleDateFormat(" MMM d, "+(as24HourTime ? "HH:mm" : "hh:mmaa"));

		return (dtstart == null ? "" : c.getString(R.string.FromPrompt) + formatter.format(new Date(dtstart*1000)))
				+ (due != null ? (duration != null ? " -" : c.getString(R.string.DuePrompt)) + formatter.format(new Date(due*1000)):"")
				+ (completed != null ? (due != null || dtstart != null ? ", " : "") + c.getString(R.string.CompletedPrompt) + formatter.format(new Date(completed*1000)):"")
				;
	}

	/**
	 * They're only equal if they're the same object, or if their resourceId is > 0 and equal
	 * @param another
	 * @return Whether these two events are equal
	 */
	@Override
	public boolean equals( Object another ) {
		return (this == another
					|| (another instanceof SimpleAcalTodo && this.resourceId > 0
								&& this.resourceId == ((SimpleAcalTodo) another).resourceId ) );
	}

	
	/**
	 * Compare this SimpleAcalEvent to another.  If this is earlier than the other return a negative
	 * integer and if this is after return a positive integer.  If they are the same return 0.
	 * @param another
	 * @return -1, 1 or 0
	 */
	@Override
	public int compareTo( SimpleAcalTodo another ) {
		if ( equals(another) ) return 0;
		if ( this.due != null ) {
			if ( another.due == null || this.due < another.due ) return -1;
			if ( this.due > another.due ) return 1;
		}
		return ( this.summary.compareTo(another.summary) );
	}

	
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		StaticHelpers.writeNullableLong(dest,dtstart);
		StaticHelpers.writeNullableLong(dest,due);
		StaticHelpers.writeNullableLong(dest,duration);
		StaticHelpers.writeNullableLong(dest,completed);
		dest.writeInt(resourceId);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeInt(colour);
		dest.writeInt(priority);
		dest.writeInt(percentComplete);
		dest.writeInt(status);
		dest.writeByte( (byte) ((alarmEnabled?0x10:0) | (hasAlarm?0x08:0) | (isPending?0x01:0)) );
		dest.writeInt(operation);
	}

	SimpleAcalTodo(Parcel src) {
		dtstart = StaticHelpers.readNullableLong(src);
		due = StaticHelpers.readNullableLong(src);
		duration = StaticHelpers.readNullableLong(src);
		completed = StaticHelpers.readNullableLong(src);
		resourceId = src.readInt();
		summary = src.readString();
		location = src.readString();
		colour = src.readInt();
		priority = src.readInt();
		percentComplete = src.readInt();
		status = src.readInt();
		byte b = src.readByte();
		alarmEnabled  = ((b & 0x10) == 0x10);
		hasAlarm  = ((b & 0x08) == 0x08);
		isPending = ((b & 0x01) == 0x01);
		operation = src.readInt();
	}

	public static final Parcelable.Creator<SimpleAcalTodo> CREATOR = new Parcelable.Creator<SimpleAcalTodo>() {
		public SimpleAcalTodo createFromParcel(Parcel in) {
			return new SimpleAcalTodo(in);
		}

		public SimpleAcalTodo[] newArray(int size) {
			return new SimpleAcalTodo[size];
		}
	};

	public boolean isCompleted() {
		return (status == TODO_STATUS_COMPLETED);
	}
	
	public boolean isOverdue() {
		if ( due == null ) return false;
		if ( due > System.currentTimeMillis() / 1000L ) return false;
		return true;
	}

	public boolean isFuture() {
		if ( due == null ) return true;
		if ( due > (System.currentTimeMillis() + 86400000L * 7) / 1000L ) return true;
		return false;
	}
	
}
