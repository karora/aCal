package org.davical.acal.database.resourcesmanager.requests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.content.ContentValues;
import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.database.alarmmanager.AlarmQueueManager;
import org.davical.acal.database.alarmmanager.AlarmRow;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.ReadOnlyBlockingRequestWithResponse;
import org.davical.acal.dataservice.Collection;
import org.davical.acal.dataservice.Resource;
import org.davical.acal.davacal.VCalendar;
import org.davical.acal.davacal.VComponentCreationException;

public class RRGetUpcomingAlarms extends ReadOnlyBlockingRequestWithResponse<ArrayList<AlarmRow>> {

	private Map<Long,Collection> alarmCollections = null;
	private AcalDateTime alarmsAfter = null;

	public RRGetUpcomingAlarms() {
	    this(new AcalDateTime());
	}

	private RRGetUpcomingAlarms(AcalDateTime after) {
		super();
		alarmsAfter = after.clone();
	}

	@Override
	public void process(ReadOnlyResourceTableManager processor)	throws ResourceProcessingException {
		alarmCollections = Collection.getAllCollections(processor.getContext());
		ArrayList<AlarmRow> alarmList = new ArrayList<AlarmRow>();

		long start = alarmsAfter.getMillis();
		long end = start;
		start -= AcalDateTime.SECONDS_IN_HOUR * 36 * 1000L;
		end   += AcalDateTime.SECONDS_IN_DAY * 14 * 1000L;

		StringBuilder whereClause = new StringBuilder(ResourceTableManager.COLLECTION_ID);
		whereClause.append(" IN (");
		boolean pastFirst = false;
		for( Collection collection : alarmCollections.values() ) {
			if ( (!collection.useForEvents && !collection.useForTasks) || !collection.alarmsEnabled ) continue;
			if ( pastFirst ) whereClause.append(',');
			else pastFirst = true;
			whereClause.append(collection.collectionId);
		}
		if ( pastFirst ) {
			whereClause.append(')');
			whereClause.append(" AND (");
			whereClause.append(ResourceTableManager.LATEST_END);
			whereClause.append(" IS NULL OR ");
			whereClause.append(ResourceTableManager.LATEST_END);
			whereClause.append(" >= ");
			whereClause.append(start);
			whereClause.append(") AND (");
			whereClause.append(ResourceTableManager.EARLIEST_START);
			whereClause.append(" IS NULL OR ");
			whereClause.append(ResourceTableManager.EARLIEST_START);
			whereClause.append(" <= ");
			whereClause.append(end);
			whereClause.append(") AND (");
			whereClause.append(ResourceTableManager.RESOURCE_DATA);
			whereClause.append(" LIKE '%BEGIN:VALARM%' )");

			ArrayList<ContentValues> cvs = processor.query(null, whereClause.toString(), null, null);
			if ( Constants.debugAlarms )
			    Log.i(ResourceManager.TAG,"Found "+cvs.size()+" resources with alarms between "+start+" and "+end);

			for (ContentValues cv : cvs) {
				Resource r = Resource.fromContentValues(cv);
				try {
					VCalendar vc = (VCalendar) VCalendar.createComponentFromResource(r);
					vc.appendAlarmInstancesBetween(alarmList, AlarmQueueManager.alarmDateRange(alarmsAfter));
				}
				catch ( VComponentCreationException e ) {
					// @todo Auto-generated catch block
					Log.w(ResourceManager.TAG,"Auto-generated catch block", e);
					continue;
				}
				catch ( Exception e ) {
					// @todo Auto-generated catch block
					Log.w(ResourceManager.TAG,"Auto-generated catch block", e);
					continue;
				}
			}
		}
		Collections.sort(alarmList);
		RRGetUpcomingAlarmsResult response = new RRGetUpcomingAlarmsResult(alarmList);

		this.postResponse(response);
	}

	public class RRGetUpcomingAlarmsResult extends ResourceResponse<ArrayList<AlarmRow>> {

		private final ArrayList<AlarmRow> result;

		public RRGetUpcomingAlarmsResult(ArrayList<AlarmRow> result) {
			this.result = result;
			setProcessed();
		}

		@Override
		public ArrayList<AlarmRow> result() {return this.result;	}

	}

}
