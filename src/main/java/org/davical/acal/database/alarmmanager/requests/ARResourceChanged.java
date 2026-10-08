package org.davical.acal.database.alarmmanager.requests;

import org.davical.acal.database.alarmmanager.AlarmProcessingException;
import org.davical.acal.database.alarmmanager.AlarmQueueManager.AlarmTableManager;
import org.davical.acal.database.alarmmanager.requesttypes.AlarmRequest;
import org.davical.acal.database.resourcesmanager.ResourceChangedEvent;

/**
 * This should only be used by Alarm Manager. It is used to allow Alarm Manager to deal with resource changes.
 * @author Chris Noldus
 *
 */
public class ARResourceChanged implements AlarmRequest {

	private ResourceChangedEvent event;
	
	public ARResourceChanged(ResourceChangedEvent event) {
		this.event = event;
	}

	@Override
	public void process(AlarmTableManager processor) throws AlarmProcessingException {
		processor.processChanges(event.getChanges());
	}

	@Override
	public String getLogDescription() {
		return "Notify AlarmTableManager that resource(s) have changed";
	}

}
