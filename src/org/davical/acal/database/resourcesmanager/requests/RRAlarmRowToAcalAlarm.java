package org.davical.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;

import org.davical.acal.database.alarmmanager.AlarmRow;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequestWithResponse;
import org.davical.acal.dataservice.Resource;
import org.davical.acal.davacal.AcalAlarm;
import org.davical.acal.davacal.Masterable;
import org.davical.acal.davacal.RecurrenceId;
import org.davical.acal.davacal.VCalendar;

public class RRAlarmRowToAcalAlarm extends BlockingResourceRequestWithResponse<AcalAlarm> {

	private final AlarmRow row;
	public RRAlarmRowToAcalAlarm(AlarmRow row) {
		this.row = row;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		try {
			Resource r = null;

			//first check to see if there is a pending version
			ArrayList<ContentValues> res = processor.getPendingResources();
			for (ContentValues cv : res) {
				if (cv.getAsLong(ResourceTableManager.PEND_RESOURCE_ID) == row.resourceId)  {
					r = Resource.fromContentValues(cv);
					break;
				}
			}
			if (r == null)  r = Resource.fromContentValues(processor.getResource(row.resourceId));

			VCalendar vc = (VCalendar) VCalendar.createComponentFromResource(r);

			Masterable master = vc.getChildFromRecurrenceId(RecurrenceId.fromString(row.recurrenceId));
			ArrayList<AcalAlarm> alarms = master.getAlarms();

			for (AcalAlarm alarm : alarms) {
				if (alarm.getBlob().equals(row.getBlob())) {
					this.postResponse(new RRAlarmRowToAcalAlarmResponse(alarm));
					return;
				}
			}
		} catch (Exception e) { }
		this.postResponse(new RRAlarmRowToAcalAlarmResponse(null));
	}

	public class RRAlarmRowToAcalAlarmResponse extends ResourceResponse<AcalAlarm> {

		private final AcalAlarm result;

		public RRAlarmRowToAcalAlarmResponse (AcalAlarm result) {
			this.result = result;
		}

		@Override
		public AcalAlarm result() {
			return result;
		}

	}

}
