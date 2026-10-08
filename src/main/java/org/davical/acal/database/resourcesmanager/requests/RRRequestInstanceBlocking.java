package org.davical.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import org.davical.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;

public class RRRequestInstanceBlocking extends ReadOnlyBlockingRequestWithResponse<ContentValues> {

	public static final String TAG = "aCal RRRequestInstanceBlocking";
	long resourceId;

	public RRRequestInstanceBlocking( long resourceId) {
		super();
		this.resourceId = resourceId;
	}
	
	@Override
	public void process(ReadOnlyResourceTableManager processor) throws ResourceProcessingException {
		ArrayList<ContentValues> cv = processor.query(null, ResourceTableManager.RESOURCE_ID+" = ?", new String[]{resourceId+""}, null);
		ArrayList<ContentValues> pcv = processor.getPendingResources();
		try {
			//check pending first
			for (ContentValues val : pcv) {
				if (val.getAsLong(ResourceTableManager.PEND_RESOURCE_ID) == this.resourceId) {
					String blob = val.getAsString(ResourceTableManager.NEW_DATA);
					if ( blob == null || blob.equals("") ) {
						// this resource has been deleted
						throw new Exception("Resource deleted.");
					}
					else {
						this.postResponse(new RRRequestInstanceBlockingResult(val));
					}
					break;
				}
			}
			if ( !isProcessed() && !cv.isEmpty() ) {
				this.postResponse(new RRRequestInstanceBlockingResult(cv.get(0)));
			}
		}
		catch ( Exception e ) {
			Log.e(TAG, e.getMessage() + Log.getStackTraceString(e));
			this.postResponse(new RRRequestInstanceBlockingResult(e));
			this.setProcessed();
		}
	}

	public class RRRequestInstanceBlockingResult extends ResourceResponse<ContentValues> {

		private ContentValues result;
		
		public RRRequestInstanceBlockingResult(ContentValues result) { 
			this.result = result;
			setProcessed();
		}
		public RRRequestInstanceBlockingResult(Exception e) { super(e); }
		
		@Override
		public ContentValues result() {return this.result;	}
		
	}
	
}
