package org.davical.acal.database.cachemanager.requests;

import java.util.ArrayList;
import java.util.TimeZone;

import android.content.ContentValues;

import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.database.cachemanager.CacheTableManager;
import org.davical.acal.database.cachemanager.CacheObject;
import org.davical.acal.database.cachemanager.CacheProcessingException;
import org.davical.acal.database.cachemanager.CacheRequestWithResponse;
import org.davical.acal.database.cachemanager.CacheResponse;
import org.davical.acal.weekview.WeekViewCache;

public class CRObjectsInWindow  extends CacheRequestWithResponse<ArrayList<CacheObject>> {

	private final WeekViewCache caller;

	/**
	 * Request all CacheObjects in the range provided. Pass the result to the callback provided
	 * @param range
	 * @param callBack
	 */
	public CRObjectsInWindow(WeekViewCache caller) {
		super(caller);
		this.caller = caller;
	}

	@Override
	public void process(CacheTableManager processor)  throws CacheProcessingException{
		final ArrayList<CacheObject> result = new ArrayList<CacheObject>();
		AcalDateRange range = caller.getWindow().getRequestedWindow();

		//No longer need data?
		if (range == null) {
			this.postResponse(new CRObjectsInWindowResponse<ArrayList<CacheObject>>(result, null));
			return;
		}

		//is data available?
		if (!processor.checkWindow(range)) {
			//Wait give up - caller can decide to rerequest or waitf for cachechanged notification
			this.postResponse(new CRObjectsInWindowResponse<ArrayList<CacheObject>>(result, range));
			return;
		}

		String dtStart = range.start.getMillis()+"";
		String dtEnd = range.end.getMillis()+"";
		String offset = TimeZone.getDefault().getOffset(range.start.getMillis())+"";


		ArrayList<ContentValues> data = processor.queryInRange(range, null);

		for (ContentValues cv : data)
				result.add(CacheObject.fromContentValues(cv));
		caller.getWindow().expandWindow(range);
		this.postResponse(new CRObjectsInWindowResponse<ArrayList<CacheObject>>(result,range));
	}

	/**
	 * This class represents the response from a CRObjectsInRange Request. It will be passed to the callback if one was provided.
	 * @author Chris Noldus
	 *
	 * @param <E>
	 */
	public class CRObjectsInWindowResponse<E extends ArrayList<CacheObject>> implements CacheResponse<ArrayList<CacheObject>> {

		private final ArrayList<CacheObject> result;
		private final AcalDateRange range;

		private CRObjectsInWindowResponse(ArrayList<CacheObject> result, AcalDateRange range) {
			this.result = result;
			this.range = range;
		}

		public AcalDateRange rangeRetreived() {
			return this.range;
		}

		/**
		 * Returns the result of the original Request.
		 */
		public ArrayList<CacheObject> result() {
			return this.result;
		}
	}
}
