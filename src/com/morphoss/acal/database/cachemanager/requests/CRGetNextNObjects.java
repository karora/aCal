package com.morphoss.acal.database.cachemanager.requests;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.ContentValues;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.cachemanager.BlockingCacheRequestWithResponse;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.cachemanager.CacheProcessingException;
import com.morphoss.acal.database.cachemanager.CacheResponse;

public class CRGetNextNObjects extends BlockingCacheRequestWithResponse<ArrayList<CacheObject>> {

	private int numObjects;
	private String cacheObjectType = null;
	
	public CRGetNextNObjects(int numObjects) {
		this.numObjects = numObjects;
	}

	public static CRGetNextNObjects GetNextNEvents(int numObjects) {
		CRGetNextNObjects result = new CRGetNextNObjects(numObjects);
		result.cacheObjectType = CacheTableManager.RESOURCE_TYPE_VEVENT;
		return result;
	}

	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		// TODO Auto-generated method stub
		AcalDateTime start = new AcalDateTime().applyLocalTimeZone();
		AcalDateTime end = start.clone().addDays(7).applyLocalTimeZone();
		int offset = TimeZone.getDefault().getOffset(start.getMillis());

		AcalDateRange range = new AcalDateRange(start,end);
		
        ArrayList<ContentValues> data = processor.query(null,
                CacheManager.whereClauseForRange(range,cacheObjectType),
				null,
				CacheTableManager.FIELD_DTSTART+" + CASE WHEN "+CacheTableManager.FIELD_DTSTART_FLOAT+" THEN "+offset+" ELSE 0 END ASC " +
						"LIMIT "+this.numObjects);
		
		ArrayList<CacheObject> res = new ArrayList<CacheObject>();
		for (ContentValues obj : data) res.add(CacheObject.fromContentValues(obj));
		
		this.postResponse(new CRGetNextNObjectsResponse(res));
	}

	
    private class CRGetNextNObjectsResponse implements CacheResponse<ArrayList<CacheObject>> {

		private ArrayList<CacheObject> result = null;
		public CRGetNextNObjectsResponse(ArrayList<CacheObject> result) {
			this.result = result;
		}
		@Override
		public ArrayList<CacheObject> result() {
			return result;
		}
		
	}
}
