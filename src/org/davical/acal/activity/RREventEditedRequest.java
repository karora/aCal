package org.davical.acal.activity;

import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.database.resourcesmanager.requesttypes.ResourceRequestWithResponse;
import org.davical.acal.dataservice.EventInstance;
import org.davical.acal.dataservice.Resource;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponent;
import org.davical.acal.davacal.VEvent;


public class RREventEditedRequest extends ResourceRequestWithResponse<Long> {

	private static final String TAG = "aCal RREventEditedRequest";
	private final EventInstance event;
	private final int action;
	private final int instances;

	public RREventEditedRequest(ResourceResponseListener<Long> callBack, EventInstance event, int action, int instances) {
		super(callBack);
		this.action = action;
		this.event = event;
		this.instances = instances;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		String newBlob = "";
		String oldBlob = null;
		String uid = null;

		try {
			if (action == EventEdit.ACTION_EDIT || action == EventEdit.ACTION_DELETE) {
				Resource res = Resource.fromContentValues(
						processor.query(null, ResourceTableManager.RESOURCE_ID+" = ?",
								new String[]{event.getResourceId()+""},null)
								.get(0));
				oldBlob = res.getBlob();
				VCalendar vc = ((VCalendar)VComponent.createComponentFromResource(res));
				newBlob = vc.applyEventAction(event, action, instances);
				uid = vc.getMasterChild().getUID();

			} else {
				VEvent comp = new VEvent(event);
				newBlob = comp.getTopParent().getCurrentBlob();  // We need to get the blob from the VCALENDAR not the VEVENT.
				uid = comp.getUID();
			}

			if ( ResourceManager.DEBUG ) Log.println(Constants.LOGD, TAG,
					"Adding Pending Table row for collection ID:"+event.getCollectionId()+", resource ID: "+event.getResourceId());
			long result = processor.addPending(event.getCollectionId(), event.getResourceId(), oldBlob, newBlob, uid);
			Log.i(TAG, "addPending returned: " + result);
			if ( result < 0 ) {
				Log.i(TAG, "Calling fail() due to negative result");
				this.fail();
			} else {
				Log.i(TAG, "Calling postResponse with result: " + result);
				this.postResponse(new RREventEditedResponse(result));
			}

		}
		catch ( Exception e ) {
			Log.w(TAG, "Failed to add to Pending Table row for collection ID:" + event.getCollectionId()
					+ ", resource ID: " + event.getResourceId(), e);
			this.fail();
			return;
		}
	}

	private void fail() {
		Log.i(TAG, "fail() called, posting null response");
		this.postResponse(new RREventEditedResponse(null));
	}


	public class RREventEditedResponse extends ResourceResponse<Long> {

		private final Long resource;

		//Can be null if failed.
		public RREventEditedResponse(Long r) {
			this.resource = r;
		}

		@Override
		public Long result() {
			return resource;
		}

	}


}
