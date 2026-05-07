package org.davical.acal.database.alarmmanager.requests;

import org.davical.acal.database.alarmmanager.AlarmProcessingException;
import org.davical.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import org.davical.acal.database.alarmmanager.AlarmRow;
import org.davical.acal.database.alarmmanager.requesttypes.AlarmResponse;
import org.davical.acal.database.alarmmanager.requesttypes.BlockingAlarmRequestWithResponse;

/**
 * Use this to get the next due alarm.
 *
 * @author Chris Noldus
 *
 */
public class ARGetNextDueAlarm extends BlockingAlarmRequestWithResponse<AlarmRow> {

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		AlarmRow res = processor.getNextAlarmPast();
		this.postResponse(new ARGetNextAlarmResult(res));
	}

	public class ARGetNextAlarmResult extends AlarmResponse<AlarmRow> {

		private final AlarmRow result;

		public ARGetNextAlarmResult(AlarmRow result) {
			this.result = result;
		}

		@Override
		public AlarmRow result() {return this.result;	}

	}

	@Override
	public String getLogDescription() {
		return "Request next due alarm";
	}
}
