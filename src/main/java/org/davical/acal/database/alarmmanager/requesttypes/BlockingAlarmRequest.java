package org.davical.acal.database.alarmmanager.requesttypes;

public interface BlockingAlarmRequest extends AlarmRequest {
	public boolean isProcessed();
}
