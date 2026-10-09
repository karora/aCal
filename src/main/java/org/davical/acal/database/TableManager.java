package org.davical.acal.database;

import java.util.ArrayList;

import android.content.ContentValues;

public interface TableManager {
    public enum QUERY_ACTION { INSERT, UPDATE, DELETE, PENDING_RESOURCE };

    public ArrayList<ContentValues> query(String[] columns, String selection, String[] selectionArgs, String orderBy);
    public int delete(String whereClause, String[] whereArgs);
    public int update(ContentValues values, String whereClause, String[] whereArgs);
    public long insert(ContentValues values);
}
