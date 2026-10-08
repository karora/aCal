package org.davical.acal.database;

import android.content.ContentValues;

public class DMInsertQuery implements DMAction {
	private final String nullColumnHack;
	private final ContentValues values;

	public DMInsertQuery(String nullColumnHack, ContentValues values) {
		this.nullColumnHack = nullColumnHack;
		this.values = values;
	}

	public void process(TableManager dm) {
		dm.insert(values);
	}
}
