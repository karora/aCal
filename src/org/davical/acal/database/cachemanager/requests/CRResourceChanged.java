package org.davical.acal.database.cachemanager.requests;

import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.DMQueryList;
import org.davical.acal.database.cachemanager.CacheManager;
import org.davical.acal.database.cachemanager.CacheTableManager;
import org.davical.acal.database.cachemanager.CacheProcessingException;
import org.davical.acal.database.cachemanager.CacheRequest;

public class CRResourceChanged implements CacheRequest {

	private DMQueryList queries;
	public static final String TAG = "aCal CRResourceChanged";
	
	public CRResourceChanged(DMQueryList queries) {
		this.queries = queries;
	}
	
	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		if ( CacheManager.DEBUG ) Log.println(Constants.LOGD, TAG, "Processing query set");
		processor.processActions(queries);
		if ( CacheManager.DEBUG ) Log.println(Constants.LOGD, TAG,"Done");
	}

}
