package org.davical.acal.database.resourcesmanager.requests;

import org.davical.acal.database.DMQueryList;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;

public class RRBlockAndProcessQueryList implements BlockingResourceRequest {

	private boolean isProcessed;
	private boolean wasSuccessful = false;
	private DMQueryList list;


	public RRBlockAndProcessQueryList(DMQueryList list) {
		this.list = list;
	}

	@Override
	public boolean isProcessed() {
		return isProcessed;
	}

	/**
	 * Whether the query list was committed to the database successfully. Only
	 * meaningful once isProcessed() is true (e.g. after sendBlockingRequest).
	 */
	public boolean wasSuccessful() {
		return wasSuccessful;
	}

	@Override
	public void process(WriteableResourceTableManager processor)
			throws ResourceProcessingException {
		wasSuccessful = processor.processActions(list);
		this.setProcessed();

	}

	@Override
	public void setProcessed() {
		this.isProcessed = true;
	}

}
