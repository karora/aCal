package org.davical.acal.database.alarmmanager.requesttypes;


public interface AlarmResponseListener<E> {
	public void alarmResponse(AlarmResponse<E> response);
}
