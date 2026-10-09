package org.davical.acal.database;

public class DMDeleteQuery implements DMAction {
	private final String whereClause;
	private final String[] whereArgs;

	public DMDeleteQuery(String whereClause, String[] whereArgs) {
		this.whereClause = whereClause;
		this.whereArgs = whereArgs;
	}

	@Override
	public void process(TableManager dm) {
		dm.delete(whereClause, whereArgs);			
	}
}
