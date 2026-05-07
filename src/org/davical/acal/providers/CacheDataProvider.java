/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.davical.acal.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import org.davical.acal.database.AcalDBHelper;

/**
 * <p>This ContentProvider interfaces with the event_cache table in the database.</p>
 *
 * <p>
 * This class accepts URI specifiers for it's operation in the following forms:
 * </p>
 * <ul>
 * <li>content://cache</li>
 * <li>content://cache/begin - Can only be used with update(), starts a new transaction.</li>
 * <li>content://cache/approve - Can only be used with update(), approves all changes since transaction started.</li>
 * <li>content://cache/commit - Ends transaction. Changes are only commited if transaction has been approved.</li>
 * <li>content://cache/# - A specific instance of a cached event</li>
 * <li>content://cache/resource/# - All cached events for a specific source event</li>
 * <li>content://cache/meta/* - Query/Write the cache metadata</li>
 * </ul>
 *
 * @author Morphoss Ltd
 *
 */
public class CacheDataProvider extends ContentProvider {

	//Authority must match one defined in manifest!
	public static final String AUTHORITY = "org.davical.acal.cache";
    public static final Uri CONTENT_URI = Uri.parse("content://"+ AUTHORITY);
    public static final Uri META_URI = Uri.withAppendedPath(CONTENT_URI,"meta");

    //Database + Table
    private SQLiteDatabase mAcalDB;
    static final String DATABASE_TABLE = "event_cache";
    static final String QUERY_TABLE = "dav_server LEFT JOIN dav_collection ON (dav_server._id=dav_collection.server_id) " +
    		"LEFT JOIN event_cache ON (dav_collection._id=event_cache.collection_id)";
    static final String META_TABLE = "event_cache_meta";

    //Path definitions
    private static final int ROOT = 0;
    private static final int ALLSETS = 1;
    private static final int ROW_ID_SET = 2;
    private static final int RESOURCE_ID_SET = 4;
    private static final int BEGIN_TRANSACTION = 5;
    private static final int END_TRANSACTION = 6;
    private static final int APPROVE_TRANSACTION = 7;
    private static final int META_QUERY = 8;

    //Creates Paths and assigns Path Definition Id's
    public static final UriMatcher uriMatcher = new UriMatcher(ROOT);
    static{
         uriMatcher.addURI(AUTHORITY, null, ALLSETS);
         uriMatcher.addURI(AUTHORITY, "#", ROW_ID_SET);
         uriMatcher.addURI(AUTHORITY, "resource/*", RESOURCE_ID_SET);
         uriMatcher.addURI(AUTHORITY, "meta", META_QUERY);
         uriMatcher.addURI(AUTHORITY, "begin", BEGIN_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "commit", END_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "approve", APPROVE_TRANSACTION);
    }

	//Table Fields - All other classes should use these constants to access fields.
    public static final String _ID = "_id";
	public static final String RESOURCE_ID = "resource_id";
    public static final String RESOURCE_TYPE = "resource_type";
    public static final String RECURRENCE_ID = "recurrence_id";
    public static final String COLLECTION_ID = "collection_id";
    public static final String SUMMARY = "summary";
    public static final String LOCATION = "location";
    public static final String DTSTART = "dtstart";
    public static final String DTEND = "dtend";
    public static final String COMPLETED = "completed";
    public static final String DTSTARTFLOAT = "dtstartfloat";
    public static final String DTENDFLOAT = "dtendfloat";
    public static final String COMPLETEDFLOAT = "completedfloat";
    public static final String FLAGS = "flags";

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        Context context = getContext();
        AcalDBHelper dbHelper = new AcalDBHelper(context);
        mAcalDB = dbHelper.getWritableDatabase();
        return (mAcalDB == null)?false:true;
    }

	/*
	 * 	(non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		int count=0;
        switch ( uriMatcher.match(uri) ) {
            case ALLSETS:
    			count = mAcalDB.delete( DATABASE_TABLE, selection, selectionArgs);
    			break;
    		case ROW_ID_SET:
    			String row_id = uri.getPathSegments().get(0);
    			count = mAcalDB.delete(
    					DATABASE_TABLE,
    					_ID + " = " + row_id +
    					(!TextUtils.isEmpty(selection) ? " AND (" +
    							selection + ')' : ""),
    							selectionArgs);
    			break;
    		case RESOURCE_ID_SET:
    		    String resource_id = uri.getPathSegments().get(1);
    			count = mAcalDB.delete(
    					DATABASE_TABLE,
    					RESOURCE_ID + " = " + resource_id +
    					(!TextUtils.isEmpty(selection) ? " AND (" +
    							selection + ')' : ""),
    							selectionArgs);
    			break;
            case META_QUERY:
                count = mAcalDB.delete( META_TABLE, selection, selectionArgs);
                break;
    		default: throw new IllegalArgumentException(
    				"Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri uri) {
        switch ( uriMatcher.match(uri) ) {
        // Get all Servers
            case ALLSETS:
            case RESOURCE_ID_SET:
                return "vnd.android.cursor.dir/vnd.morphoss.event_cache";
            case ROW_ID_SET:
                return "vnd.android.cursor.item/vnd.morphoss.event_cache";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {

		//---add a new server---
		long rowID = mAcalDB.insert( (uriMatcher.match(uri) == META_QUERY ? META_TABLE : DATABASE_TABLE), null, values);

		//---if added successfully---
		if (rowID>0)
		{
			Uri _uri = ContentUris.withAppendedId(uri, rowID);
			getContext().getContentResolver().notifyChange(_uri, null);
			return _uri;
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		sqlBuilder.setTables(QUERY_TABLE);

		String groupBy = null;
        switch ( uriMatcher.match(uri) ) {
            case ROW_ID_SET:
                //---if getting a particular row---
                sqlBuilder.appendWhere(_ID + " = " + uri.getPathSegments().get(0));
                break;

            case RESOURCE_ID_SET:
                //---if getting a particular resource---
                sqlBuilder.appendWhere(RESOURCE_ID + " = " + uri.getPathSegments().get(1));
                break;

            case META_QUERY:
                sqlBuilder.setTables(META_TABLE);
                if ( projection == null )
                    projection = new String[] { META_TABLE + ".*" };
        }
        if ( projection == null )
            projection = new String[] { DATABASE_TABLE + ".*" };

		Cursor c = sqlBuilder.query(
				mAcalDB,
				projection,
				selection,
				selectionArgs,
				groupBy,
				null,
				sortOrder);

		//---register to watch a content URI for changes---
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 *
	 * I'm tempted to leave this out.  I think that the general approach for 'update'
	 * in this case is to do:
	 * beginTransaction()
	 * delete(set)
	 * insert()
	 * insert()
	 * insert()
	 * .
	 * .
	 * .
	 * commitTransaction()
	 *
	 * Of course we could also code in this routine to accept a list of some kind and
	 * do exactly that...
	 *
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int count = 0;

		switch (uriMatcher.match(uri)){
    		case ALLSETS:
    			count = mAcalDB.update(
    					DATABASE_TABLE,
    					values,
    					selection,
    					selectionArgs);
    			break;
    		case ROW_ID_SET:
    			count = mAcalDB.update(
    					DATABASE_TABLE,
    					values,
    					_ID + " = " + uri.getPathSegments().get(0) +
    					(!TextUtils.isEmpty(selection) ? " AND (" +
    							selection + ')' : ""),
    							selectionArgs);
    			break;
    		case RESOURCE_ID_SET:
    			count = mAcalDB.update(
    					DATABASE_TABLE,
    					values,
    					RESOURCE_ID + " = " + uri.getPathSegments().get(1) +
    					(!TextUtils.isEmpty(selection) ? " AND (" +
    							selection + ')' : ""),
    							selectionArgs);
    			break;
    		case BEGIN_TRANSACTION:	//Return 1 for success or 0 for failure
    				//We are beginning a new transaction only (at this time we wont allow nested tx's)
    				if (mAcalDB.inTransaction()) return 0;
    				mAcalDB.beginTransaction();
    				return 1;

    		case END_TRANSACTION:	//Return 1 for success or 0 for failure
    			//We are ending an existing transaction only
    			if (!mAcalDB.inTransaction()) return 0;
    			mAcalDB.endTransaction();
    			return 1;
    		case APPROVE_TRANSACTION:	//Return 1 for success or 0 for failure
    			//We are ending an existing transaction only
    			if (!mAcalDB.inTransaction()) return 0;
    			mAcalDB.setTransactionSuccessful();
    			return 1;
            case META_QUERY:
                count = mAcalDB.update( META_TABLE, values, selection, selectionArgs);
                break;
    		default: throw new IllegalArgumentException( "Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

}
