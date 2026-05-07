package org.davical.acal.database.cachemanager;

public interface BlockingCacheRequest extends CacheRequest {
	public boolean isProcessed();
}
