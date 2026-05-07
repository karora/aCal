package org.davical.acal.database.cachemanager.requests;

import org.davical.acal.database.cachemanager.CacheTableManager;
import org.davical.acal.database.cachemanager.CacheProcessingException;
import org.davical.acal.database.cachemanager.CacheRequest;

public class CRClearCacheRequest implements CacheRequest {

	@Override
	public void process(CacheTableManager processor) throws CacheProcessingException {
		processor.rebuildCache();
	}

}
