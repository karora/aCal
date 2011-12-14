package com.morphoss.acal.database.cachemanager;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.ContentValues;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.cachemanager.CacheManager.CacheTableManager;

/**
 * A CacheRequest that returns a List of CacheObjects that occur in the specified range.
 * 
 * To get the result you should pass in a CacheResponseListenr of the type ArrayList&lt;CacheObject&gt;
 * If you don't care about the result (e.g. your forcing a window size change) you may pass a null callback.
 * 
 * @author Chris Noldus
 *
 */
public class CRObjectsInRange extends CacheRequestWithResponse<ArrayList<CacheObject>> {

	private AcalDateRange range;
	
	/**
	 * Request all CacheObjects in the range provided. Pass the result to the callback provided
	 * @param range
	 * @param callBack
	 */
	public CRObjectsInRange(AcalDateRange range, CacheResponseListener<ArrayList<CacheObject>> callBack) {
		super(callBack);
		this.range = range;
	}
	
	@Override
	public void process(CacheTableManager processor)  throws CacheProcessingException{
		final ArrayList<CacheObject> result = new ArrayList<CacheObject>();
		if (!processor.checkWindow(range)) {
			//Wait give up - caller can decide to rerequest or waitf for cachechanged notification
			this.postResponse(new CRObjectsInRangeResponse<ArrayList<CacheObject>>(result));
			return;
		}

		String dtStart = range.start.getMillis()+"";
		String dtEnd = range.end.getMillis()+"";
		String offset = TimeZone.getDefault().getOffset(range.start.getMillis())+"";
		
		
		ArrayList<ContentValues> data = processor.query(null, 
				"( " + 
					"( "+CacheTableManager.FIELD_DTEND+" > ? AND NOT "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
						" OR "+
						"( "+CacheTableManager.FIELD_DTEND+" + ? > ? AND "+CacheTableManager.FIELD_DTEND_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTEND+" ISNULL )"+
				" ) AND ( "+
					"( "+CacheTableManager.FIELD_DTSTART+" < ? AND NOT "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" + ? < ? AND "+CacheTableManager.FIELD_DTSTART_FLOAT+" )"+
						" OR "+
					"( "+CacheTableManager.FIELD_DTSTART+" ISNULL )"+
				")",
				new String[] {dtStart , offset, dtStart, dtEnd, offset, dtEnd},
				null,null,CacheTableManager.FIELD_DTSTART+" ASC");
		
		for (ContentValues cv : data) 
				result.add(CacheObject.fromContentValues(cv));
		
		this.postResponse(new CRObjectsInRangeResponse<ArrayList<CacheObject>>(result));
	}

	/**
	 * This class represents the response from a CRObjectsInRange Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
	public class CRObjectsInRangeResponse<E extends ArrayList<CacheObject>> implements CacheResponse<ArrayList<CacheObject>> {
		
		private ArrayList<CacheObject> result;
		
		private CRObjectsInRangeResponse(ArrayList<CacheObject> result) {
			this.result = result;
		}
		
		/**
		 * Returns the result of the original Request.
		 */
		public ArrayList<CacheObject> result() {
			return this.result;
		}
	}

}