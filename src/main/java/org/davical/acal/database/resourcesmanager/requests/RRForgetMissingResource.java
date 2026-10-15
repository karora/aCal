package org.davical.acal.database.resourcesmanager.requests;

import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.requesttypes.ResourceRequest;

/**
 * A resource was asked for that is not there, though something pointed at
 * it.  Whatever keeps something of it is told it has gone.
 */
public class RRForgetMissingResource implements ResourceRequest {

	private final long resourceId;

	public RRForgetMissingResource(long resourceId) {
		this.resourceId = resourceId;
	}

	private boolean processed = false;
	@Override
	public boolean isProcessed() { return this.processed; }
	@Override
	public synchronized void setProcessed() { this.processed = true; }

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		processor.forgetMissingResource(resourceId);
	}

}
