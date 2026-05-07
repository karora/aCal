package org.davical.acal.database.resourcesmanager.requests;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;

public class RRUpdateCollection implements BlockingResourceRequest {

	private boolean isProcessed = false;
	private long collectionId;
	private ContentValues data;
	
	public RRUpdateCollection(long collectionId, ContentValues collectionData) {
		this.collectionId = collectionId;
		this.data = collectionData;
	}
	
	@Override
	public boolean isProcessed() {
		return this.isProcessed;
	}

	@Override
	public void process(WriteableResourceTableManager processor)
			throws ResourceProcessingException {
	
		processor.updateCollection(collectionId, data);
		this.setProcessed();

	}

	@Override
	public void setProcessed() {
		this.isProcessed = true;
	}

}
