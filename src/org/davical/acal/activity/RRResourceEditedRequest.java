package org.davical.acal.activity;

import java.util.UUID;

import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.database.resourcesmanager.requesttypes.ResourceRequestWithResponse;
import org.davical.acal.dataservice.Resource;
import org.davical.acal.davacal.PropertyName;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;


public class RRResourceEditedRequest extends ResourceRequestWithResponse<Long> {

	private static final String TAG = "aCal RRResourceEditedRequest";
	private long collectionId;
	private long resourceId;
	private VComponent resourceComponent;
	private int action;

	public static final int ACTION_CREATE = 1;
	public static final int ACTION_UPDATE = 2;
	public static final int ACTION_DELETE = 3;

	/**
	 * Apply a resource edit to the database.
	 * @param callBack
	 * @param cid collection ID
	 * @param rid resource ID
	 * @param vc 
	 * @param action 
	 */
	public RRResourceEditedRequest(ResourceResponseListener<Long> callBack, long cid, long rid, VComponent vc, int action ) {
		super(callBack);
		this.collectionId = cid;
		this.resourceId = rid;
		this.resourceComponent = vc;
		this.action = action;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		String newBlob = "";
		String oldBlob = null;
		String uid = null;

		Resource res = null;
		try {
			if ( action == ACTION_CREATE ) {
				oldBlob = null;
			}
			else {
				res = Resource.fromContentValues(
					processor.query(null, ResourceTableManager.RESOURCE_ID+" = "+resourceId, null, null).get(0));
				oldBlob = res.getBlob();
			}

			if ( action == ACTION_DELETE )
				newBlob = null;
			else
				newBlob = resourceComponent.getCurrentBlob();

			try {
				if ( resourceComponent instanceof VCalendar )
					uid = ((VCalendar) resourceComponent).getMasterChild().getUID();
				else
					uid = resourceComponent.getProperty(PropertyName.UID).getValue();
			}
			catch( Exception e ) {
				uid = UUID.randomUUID().toString();
			}
			
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD, TAG, 
					"Adding Pending Table row for collection ID:"+collectionId+", resource ID: "+resourceId);

			long result = processor.addPending(collectionId, resourceId, oldBlob, newBlob, uid);
			
			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGI, TAG, 
					"Got result "+result+" when adding pending resource for "+collectionId+", resource ID: "+result+
					", Old:\n"+oldBlob+"\nNew:\n"+newBlob+"\n\tUID:"+uid);

			if ( result < 0 ) 
				this.fail();
			else 
				this.postResponse(new RRResourceEditedResponse(result));

			
		}
		catch ( Exception e ) {
			Log.w(TAG, "Failed to add to Pending Table row for collection ID:" + collectionId
					+ ", resource ID: " + resourceId, e);
			this.fail();
			return;
		}
	}
	
	private void fail() {
		this.postResponse(new RRResourceEditedResponse(null));
	}
	
	
	public class RRResourceEditedResponse extends ResourceResponse<Long> {

		private Long resource;
		
		//Can be null if failed.
		public RRResourceEditedResponse(Long r) {
			this.resource = r;
		}
		
		@Override
		public Long result() {
			return resource;
		}
		
	}


}
