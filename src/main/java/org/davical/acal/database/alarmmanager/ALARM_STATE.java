package org.davical.acal.database.alarmmanager;

public enum ALARM_STATE {

	PENDING,
	SNOOZED,
	DISMISSED,
	/** The notification has been posted; awaiting dismiss/snooze. Must stay last: ordinals are persisted. */
	FIRED
}
