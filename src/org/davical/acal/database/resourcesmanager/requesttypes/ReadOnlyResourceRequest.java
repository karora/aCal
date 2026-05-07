package org.davical.acal.database.resourcesmanager.requesttypes;

import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;

public interface ReadOnlyResourceRequest extends Comparable<ReadOnlyResourceRequest> {
	
	public void process(ResourceManager.ReadOnlyResourceTableManager processor) throws ResourceProcessingException;
	public boolean isProcessed();
	public void setProcessed();
	public int priority();
}
