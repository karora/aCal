package org.davical.acal.database.alarmmanager;

import java.util.Locale;

import android.content.ContentValues;
import android.util.Log;

import org.davical.acal.AcalApplication;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.davacal.VAlarm;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.providers.AlarmDataProvider;

public class AlarmRow implements Comparable<AlarmRow> {

	private final long id;
    public final long resourceId;
    public final String recurrenceId;
	public final long baseTimeToFire;
    private long timeToFire;
	private ALARM_STATE state;
	private final String blob;
	private static final long SNOOZE_MILLIS = 9*60*1000;

	public AlarmRow(long id, long base_ttf, long ttf, long rid, String rrid, ALARM_STATE state, String blob) {
		this.id = id;
        this.baseTimeToFire = base_ttf;
		this.timeToFire = ttf;
		this.resourceId = rid;
		this.recurrenceId = rrid;
		this.state = state;
		this.blob = blob;
	}

	public AlarmRow(long ttf, long rid, String rrid, ALARM_STATE state, String blob) {
		this(-1, ttf, ttf, rid,rrid,state, blob);
	}

	public AlarmRow(long ttf, long rid, String rrid, String blob) {
		this(-1, ttf, ttf, rid, rrid, ALARM_STATE.PENDING, blob);
	}

	public ContentValues toContentValues() {
		ContentValues cv = new ContentValues();
		if (id > 0) cv.put(AlarmDataProvider._ID, id);
		cv.put(AlarmDataProvider.BASE_TIME_TO_FIRE, baseTimeToFire);
        cv.put(AlarmDataProvider.TIME_TO_FIRE, timeToFire);
		cv.put(AlarmDataProvider.RESOURCE_ID, resourceId);
		cv.put(AlarmDataProvider.RRID, recurrenceId);
		cv.put(AlarmDataProvider.STATE, state.ordinal());
		cv.put(AlarmDataProvider.BLOB, blob);
		return cv;
	}

	public static AlarmRow fromContentValues(ContentValues cv) {

		if (!cv.containsKey(AlarmDataProvider._ID)) {
			cv = new ContentValues(cv);
			cv.put(AlarmDataProvider._ID, -1);
		}
		Long baseTTF = cv.getAsLong(AlarmDataProvider.BASE_TIME_TO_FIRE);
		if (baseTTF == null ) baseTTF = cv.getAsLong(AlarmDataProvider.TIME_TO_FIRE);
		return new AlarmRow(
				cv.getAsLong(AlarmDataProvider._ID),
                baseTTF,
				cv.getAsLong(AlarmDataProvider.TIME_TO_FIRE),
				cv.getAsLong(AlarmDataProvider.RESOURCE_ID),
				cv.getAsString(AlarmDataProvider.RRID),
				ALARM_STATE.values()[cv.getAsInteger(AlarmDataProvider.STATE)],
				cv.getAsString(AlarmDataProvider.BLOB)
		);

	}

	@Override
	public boolean equals(Object other) {
	    if ( other == this ) return true;
	    if ( ((AlarmRow)other).id == this.id ) return true;
        if ( ((AlarmRow)other).resourceId == this.resourceId
                && ((AlarmRow)other).recurrenceId.equals(this.recurrenceId)  ) return true;
	    return false;
	}

	@Override
	public int compareTo(AlarmRow another) {
		return (int)(this.timeToFire - another.timeToFire);
	}

	public long getTimeToFire() {
		return this.timeToFire;
	}

	public void setState(ALARM_STATE state) {
		this.state = state;

	}

	public long getId() {
		return this.id;
	}

	public String getBlob() {
		return this.blob;
	}

    public void addSnooze() {
        this.timeToFire += SNOOZE_MILLIS;
    }

    @Override
    public String toString() {
        VAlarm va = (VAlarm) VAlarm.createComponentFromBlob(getBlob());
        AcalDateTime fireAt = AcalDateTime.localTimeFromMillis(timeToFire, false);
        String summary = "Some kind of error occurred :-(";
        try {
            summary = ((VCalendar) VCalendar
                    .createComponentFromResource(
                            AcalApplication.getResourceFromDatabase(this.resourceId)
                        )
                    )
                    .getMasterChild()
                    .getSummary();
        }
        catch( Exception e) {
            Log.e("aCal", "Error", e);
        }
        return String.format(Locale.ENGLISH, "ID:%d %s %-8.8s %s", resourceId, fireAt.fmtIcal(), state.toString(), summary);
    }
}
