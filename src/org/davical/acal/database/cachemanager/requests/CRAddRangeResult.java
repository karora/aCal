package org.davical.acal.database.cachemanager.requests;

import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.acaltime.AcalDateRange;
import org.davical.acal.database.DMQueryList;
import org.davical.acal.database.cachemanager.CacheManager;
import org.davical.acal.database.cachemanager.CacheTableManager;
import org.davical.acal.database.cachemanager.CacheProcessingException;
import org.davical.acal.database.cachemanager.CacheRequest;


public class CRAddRangeResult implements CacheRequest {

	private DMQueryList queries;
	private AcalDateRange range;
	public static final String TAG = "aCal CRAddRangeResult";
	
	public CRAddRangeResult(DMQueryList queries, AcalDateRange range) {
		this.queries = queries;
		this.range = range;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		if ( CacheManager.DEBUG && Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, "Processing query set and updating window");
		processor.processActions(queries);
		processor.updateWindowToInclude(range);
		if ( CacheManager.DEBUG && Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG,"Done");
	}

	

}
