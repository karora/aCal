package com.morphoss.acal.cachemanager;

import android.os.Parcel;
import android.os.Parcelable;

import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.DefaultCollectionFactory;
import com.morphoss.acal.dataservice.EventInstance;

/**
 * Represents a single row in the cache
 * @author Chris Noldus
 *
 */
public class CacheObject implements Parcelable {
	private final long rid;
	private final long cid;
	private final String summary;
	private final String location;
	private final long start;
	private final long end;
	private final int flags;
	
	public static final int EVENT_FLAG = 			1;
	public static final int TODO_FLAG = 			1<<1;
	public static final int HAS_ALARM_FLAG = 		1<<2;
	public static final int RECURS_FLAG =			1<<3;
	public static final int DIRTY_FLAG = 			1<<4;
	public static final int START_IS_FLOATING_FLAG =1<<5;
	public static final int END_IS_FLOATING_FLAG = 	1<<6;
	public static final int FLAG_ALL_DAY = 			1<<7;
	
	public enum TYPE { EVENT, TODO };
	
	public CacheObject(long rid, long cid, String sum, String loc, long st, long end, int flags) {
		this.rid = rid;
		this.cid = cid;
		this.summary = sum;
		this.location= loc;
		this.start = st;
		this.end = end;
		this.flags = flags;;
	}
	
	private CacheObject(Parcel in) {
		rid = in.readLong();
		cid = in.readLong();
		summary = in.readString();
		location = in.readString();
		start = in.readLong();
		end = in.readLong();
		flags = in.readInt();

	}
	
	public EventInstance getFullInstance() {
		//TODO
		return null;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(rid);
		dest.writeLong(cid);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeLong(start);
		dest.writeLong(end);
		dest.writeInt(flags);
	}
	
	public static final Parcelable.Creator<CacheObject> CREATOR = new Parcelable.Creator<CacheObject>() {
		public CacheObject createFromParcel(Parcel in) {
			return new CacheObject(in);
		}

		public CacheObject[] newArray(int size) {
			return new CacheObject[size];
		}
	};

	public boolean isPending() {
		return (flags&DIRTY_FLAG)>0;
	}

	public String getSummary() {
		return this.summary;
	}

	public boolean hasAlarms() {
		return (flags&HAS_ALARM_FLAG)>0;
	}
	
	public Collection getCollection() {
		return Collection.getInstance(new DefaultCollectionFactory(), cid);
	}

	public boolean isRecuring() {
		return (flags&RECURS_FLAG)>0;
	}

	public String getLocation() {
		return this.location;
	}

	public long getStart() {
		return start;
	}
	public long getEnd() {
		return end;
	}

	public boolean isAllDay() {
		return (flags&FLAG_ALL_DAY)>0;
	}
}
