package org.davical.acal.dataservice;

import java.util.ArrayList;

import android.content.ContentValues;

import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.acaltime.AcalDuration;
import org.davical.acal.davacal.AcalAlarm;
import org.davical.acal.davacal.Masterable;
import org.davical.acal.davacal.RecurrenceId;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;
import org.davical.acal.davacal.VEvent;
import org.davical.acal.davacal.VJournal;
import org.davical.acal.davacal.VTodo;

public abstract class CalendarInstance {

	protected long collectionId;
	protected long resourceId;
	protected AcalDateTime dtstart;
	protected AcalDateTime dtend;
	protected ArrayList<AcalAlarm> alarms;
	protected String rrule;
	protected String rrid;
	protected String summary;
	protected String location;
	protected String description;
	protected boolean isFirstInstance;


	/**
	 * Default constructor. Nulls can be applied to any variable. The only constraint is that cid is a valid collection Id.
	 * @param cid CollectionId
	 * @param rid ResourceID (negative means new)
	 * @param start Start time
	 * @param end End time
	 * @param alarms
	 * @param rrule Recurrence Rule (null if there is none)
	 * @param summary
	 * @param location
	 * @param description
	 */
	protected CalendarInstance(long cid, long rid, AcalDateTime start, AcalDateTime end, ArrayList<AcalAlarm> alarms, String rrule,
			String rrid, String summary, String location, String description, boolean firstInstance) {

		this.collectionId = cid; if (cid < 0) throw new IllegalArgumentException("Collection ID must be a valid collection!");
		this.resourceId = (rid<0) ? -1 : rid;
		this.dtstart = start;
		this.dtend = end;
		this.alarms = (alarms == null) ? this.alarms = new ArrayList<AcalAlarm>() : alarms;
		this.rrule = rrule;
		this.rrid = rrid;
		this.summary = (summary == null) ? "" : summary;
		this.location = (location == null) ? "" : location;
		this.description = (description == null) ? "" : description;
		this.isFirstInstance = firstInstance;
	}

	protected CalendarInstance(VCalendar calendar, long collectionId, long resourceId, RecurrenceId rrid ) {
		this( calendar.getChildFromRecurrenceId(rrid), collectionId, resourceId, rrid, false);
	}

	protected CalendarInstance(Masterable masterInstance, long collectionId, long resourceId, RecurrenceId rrid, boolean delete) {
		this(collectionId,
				resourceId,
				masterInstance.getStart(),
				masterInstance.getEnd(),
				null,
				masterInstance.getRRule(),
				(rrid == null ? null : rrid.toRfcString()),
				masterInstance.getSummary(),
				masterInstance.getLocation(),
				masterInstance.getDescription(),
				masterInstance.isMasterInstance()
			 );

		// Any time we supply a recurrence ID we are overriding the start / end for this masterInstance
		if ( rrid != null ) {
		    AcalDuration d = getDuration();
		    dtstart = rrid.when.clone();
		    dtend = dtstart.clone().addDuration(d);
		}
		// We do this here in case they were overridden.
        alarms = masterInstance.getAlarms(dtstart,dtend);
	}

	public AcalDateTime getEnd() {
		return this.dtend;
	}

	//getters
	public AcalDuration getDuration() {
		if (dtstart == null) return null;
		return dtstart.getDurationTo(getEnd());
	}

	public boolean isFirstInstance() {
		return this.isFirstInstance;
	}

	public AcalDateTime getStart() { return (dtstart  == null) ? null : this.dtstart.clone(); };
	public ArrayList<AcalAlarm> getAlarms() { return alarms; }
	public String getRRule() { return this.rrule; }
	public String getSummary() { return this.summary; }
	public String getLocation() { return location; }
	public String getDescription() { return this.description; }
	public boolean isSingleInstance() { return (rrule == null || rrule.equals("")); }
	public long getCollectionId() { return this.collectionId; }
	public long getResourceId() { return this.resourceId; }
	public String getRecurrenceId() { return this.rrid; }


	public void setAlarms(ArrayList<AcalAlarm> alarms) {
		this.alarms = (alarms == null) ? this.alarms = new ArrayList<AcalAlarm>() : alarms;
	}
	public void setCollectionId(long cid) {
		if (cid < 0) throw new IllegalArgumentException("Collection ID must be a valid collection!");
		this.collectionId = cid;
	}
	public void setDates(AcalDateTime start, AcalDateTime end) {
		this.dtstart = start.clone();
		this.dtend = end.clone();
	}
	public void setStartDate(AcalDateTime start) {
		this.dtstart = start.clone();
	}
	public void setEndDate(AcalDateTime end) {
		this.dtend = end.clone();
	}
	public void setSummary(String summary) {
		this.summary = (summary == null) ? "" : summary;
	}
	public void setDescription(String newDesc) {
		this.description = (newDesc == null) ? "" : newDesc;
	}
	public void setLocation(String newLoc) {
		this.location = (newLoc == null) ? "" : newLoc;
	}
	public void setRepeatRule(String newRule) {
		this.rrule = newRule;
	}


	static public CalendarInstance getInstance(Masterable masterInstance, long collectionId, long resourceId, RecurrenceId rrid ) {
		if ( masterInstance instanceof VEvent ) {
			return new EventInstance((VEvent) masterInstance, collectionId, resourceId, rrid);
		}
		else if ( masterInstance instanceof VTodo ) {
			return new TodoInstance((VTodo) masterInstance, collectionId, resourceId, rrid);
		}
		else if ( masterInstance instanceof VJournal ) {
			return new JournalInstance((VJournal) masterInstance, collectionId, resourceId, rrid);
		}
		else {
			throw new IllegalArgumentException("Resource does not map to a known Component Type");
		}
	}


	public static CalendarInstance fromResourceAndRRId(Resource res, String rrid) throws IllegalArgumentException {
		VComponent comp = VComponent.createComponentFromBlob(res.getBlob());
		if (!(comp instanceof VCalendar)) throw new IllegalArgumentException("Resource provided is not a VCalendar");
		Masterable obj;
		if ( rrid == null )
			obj = ((VCalendar)comp).getMasterChild();
		else {
			obj = ((VCalendar)comp).getChildFromRecurrenceId(RecurrenceId.fromString(rrid));
		}

		CalendarInstance inst =  getInstance(obj, res.getCollectionId(), res.getResourceId(), obj.getRecurrenceId() );
		return inst;
	}


	public static CalendarInstance fromPendingRowAndRRID(ContentValues val,	String rrid) {
		Resource res = Resource.fromContentValues(val);
		return fromResourceAndRRId(res,rrid);
	}

}
