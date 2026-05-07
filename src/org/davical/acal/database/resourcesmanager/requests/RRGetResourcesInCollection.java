package org.davical.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;

import org.davical.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;
import org.davical.acal.dataservice.Resource;

public class RRGetResourcesInCollection extends ReadOnlyBlockingRequestWithResponse<ArrayList<Resource>> {

	private long collectionId;
	
	public RRGetResourcesInCollection(long collectionId) {
		this.collectionId = collectionId;
	}
	@Override
	public void process(ReadOnlyResourceTableManager processor)	throws ResourceProcessingException {
		ArrayList<Resource> response = new ArrayList<Resource>();
		
		//DO PROCESSING HERE
		ArrayList<ContentValues> cvs = processor.query(null, ResourceTableManager.COLLECTION_ID+" = ?", new String[]{collectionId+""},
														null);
		
		for (ContentValues cv : cvs) response.add(Resource.fromContentValues(cv));
		
		this.postResponse(new RRGetResourcesInCollectionResult(response));
	}

	public class RRGetResourcesInCollectionResult extends ResourceResponse<ArrayList<Resource>> {

		private ArrayList<Resource> result;
		
		public RRGetResourcesInCollectionResult(ArrayList<Resource> result) { this.result = result; }
		
		@Override
		public ArrayList<Resource> result() {return this.result;	}
		
	}
}
