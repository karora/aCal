package org.davical.acal.database.resourcesmanager.requesttypes;

import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;

public interface ResourceRequest {

	public boolean isProcessed();
	public void setProcessed();
	
	public void process(ResourceManager.WriteableResourceTableManager processor) throws ResourceProcessingException;

}
