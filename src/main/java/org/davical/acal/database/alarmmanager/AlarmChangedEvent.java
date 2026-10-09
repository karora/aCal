package org.davical.acal.database.alarmmanager;

import java.util.ArrayList;

import org.davical.acal.database.DataChangeEvent;

public class AlarmChangedEvent {
private ArrayList<DataChangeEvent> changes;
	
	public AlarmChangedEvent(ArrayList<DataChangeEvent> changes) {
		this.changes = changes;
	}
	
	public ArrayList<DataChangeEvent> getChanges() { return this.changes; }
}
