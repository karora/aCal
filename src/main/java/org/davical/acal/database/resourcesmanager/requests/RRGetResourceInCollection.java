package org.davical.acal.database.resourcesmanager.requests;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;

public class RRGetResourceInCollection extends
		ReadOnlyBlockingRequestWithResponse<ContentValues> {

	private long collectionId;
	private String responseHref;
	
	public RRGetResourceInCollection(long collectionId, String responseHref) {
		this.collectionId = collectionId;
		this.responseHref = responseHref;
	}
	@Override
	public void process(ReadOnlyResourceTableManager processor)	throws ResourceProcessingException {	
		this.postResponse(new RRGetResourceInCollectionResult(processor.getResourceInCollection(collectionId, responseHref)));
	}

	public class RRGetResourceInCollectionResult extends ResourceResponse<ContentValues> {

		private ContentValues result;
		
		public RRGetResourceInCollectionResult(ContentValues result) { this.result = result; }
		
		@Override
		public ContentValues result() {return this.result;	}
		
	}

}
