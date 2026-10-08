package org.davical.acal.database;

import org.davical.acal.acaltime.AcalDateRange;

public interface CacheModifier {

	public void deleteRange(AcalDateRange range);
}
