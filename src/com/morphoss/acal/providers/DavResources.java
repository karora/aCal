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

package com.morphoss.acal.providers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.database.AcalDBHelper;

/**
 * <p>
 * This ContentProvider interfaces with the dav_resource table in the database. DavResources
 * are the actual entries: events, todo items, journal notes & vcards held within collections.
 * </p>
 * <p>
 * This class accepts URI specifiers for it's operation in the following forms:
 * </p>
 * <ul>
 * <li>content://resources</li>
 * <li>content://resources/#</li>
 * <li>content://resources/collection/#</li>
 * <li>content://resources/server/#</li>
 * </ul>
 * 
 * @author Morphoss Ltd
 *
 */
public class DavResources extends ContentProvider {

	public static final String TAG = "aCal DavResources";

	//Authority must match one defined in manifest!
	public static final String AUTHORITY = "resources";
    public static final Uri CONTENT_URI = Uri.parse("content://"+ AUTHORITY);
    
    //Database + Table
    private SQLiteDatabase AcalDB;
    public static final String DATABASE_TABLE = "dav_resource";
    
    //Path definitions
    private static final int ROOT = 0;
    private static final int ALL_RESOURCES = 1;
    private static final int BY_RESOURCE_ID = 2;   
    private static final int BY_COLLECTION_ID = 3;   
    private static final int BY_SERVER_ID = 4;   
    private static final int BEGIN_TRANSACTION = 5;
    private static final int END_TRANSACTION = 6;
    private static final int APPROVE_TRANSACTION = 7;
       
    //Creates Paths and assigns Path Definition Id's
    public static final UriMatcher uriMatcher = new UriMatcher(ROOT);
    static{
         uriMatcher.addURI(AUTHORITY, null, ALL_RESOURCES);
         uriMatcher.addURI(AUTHORITY, "#", BY_RESOURCE_ID);
         uriMatcher.addURI(AUTHORITY, "collection/#", BY_COLLECTION_ID);
         uriMatcher.addURI(AUTHORITY, "server/#", BY_SERVER_ID);
         uriMatcher.addURI(AUTHORITY, "begin", BEGIN_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "commit", END_TRANSACTION);
         uriMatcher.addURI(AUTHORITY, "approve", APPROVE_TRANSACTION);
    }

	//Table Fields - All other classes should use these constants to access fields.
    /**
     *
CREATE TABLE dav_resource (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  collection_id INTEGER REFERENCES dav_collection(_id),
  name TEXT,
  etag TEXT,
  last_modified DATETIME,
  content_type TEXT, 
  data BLOB,
  UNIQUE(collection_id,name)
);
     */
	public static final String _ID = "_id";
	public static final String COLLECTION_ID="collection_id";
	public static final String RESOURCE_NAME="name";
	public static final String ETAG="etag";
	public static final String LAST_MODIFIED="last_modified";
	public static final String CONTENT_TYPE="content_type";
	public static final String RESOURCE_DATA="data";
	public static final String NEEDS_SYNC="needs_sync";
	public static final String EARLIEST_START="earliest_start";
	public static final String LATEST_END="latest_end";

	// This is not a field, but we sometimes put this into the ContentValues as if
	// it were, when there is a pending change for this resource.
	public static final String IS_PENDING="is_pending";
	
	/*
	 * <p>Delete matching rows from the dav_resource table</p>
	 * 
	 * @author Morphoss Ltd
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		
		if (Constants.LOG_DEBUG)Log.d(TAG,"Deleting resources with: "+selection);
		int count=0;
		switch (uriMatcher.match(uri)){
		case ALL_RESOURCES:
			count = AcalDB.delete(
					DATABASE_TABLE,
					selection, 
					selectionArgs);
			break;
		case BY_RESOURCE_ID:
			String id = uri.getPathSegments().get(0);
			count = AcalDB.delete(
					DATABASE_TABLE,                        
					_ID + " = " + id + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
			break;
		case BY_COLLECTION_ID:
			String collection_id = uri.getPathSegments().get(1);
			count = AcalDB.delete(
					DATABASE_TABLE,                        
					COLLECTION_ID + " = " + collection_id + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
			break;
		case BY_SERVER_ID:
			String server_id = uri.getPathSegments().get(1);
			count = AcalDB.delete(
					DATABASE_TABLE,                        
					COLLECTION_ID + " IN (SELECT " + DavCollections._ID + 
					                      " FROM " + DavCollections.DATABASE_TABLE +
					                      " WHERE " + DavCollections.SERVER_ID + " = " + server_id + ")" + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
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
		// TODO Auto-generated method stub
		switch (uriMatcher.match(uri)) {
		//Get all Servers
		case ALL_RESOURCES:
		case BY_SERVER_ID:
		case BY_COLLECTION_ID:
			return "vnd.android.cursor.dir/vnd.morphoss.resource";
		case BY_RESOURCE_ID:
			return "vnd.android.cursor.item/vnd.morphoss.resource";
		default:
			throw new IllegalArgumentException("Unsupported URI: "+uri);
		}

	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		//---add a new server---
		long rowID = AcalDB.insert(
				DATABASE_TABLE, "", values);

		//---if added successfully---
		if (rowID>0)
		{
			Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
			getContext().getContentResolver().notifyChange(_uri, null);    
			return _uri;                
		}        
		throw new SQLException("Failed to insert row into " + uri);
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		Context context = getContext();
		AcalDBHelper dbHelper = new AcalDBHelper(context);
		AcalDB = dbHelper.getWritableDatabase();
		return (AcalDB == null)?false:true;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		sqlBuilder.setTables(DATABASE_TABLE);

		if (uriMatcher.match(uri) == BY_RESOURCE_ID)
			//---if getting a particular server---
			sqlBuilder.appendWhere(_ID + " = " + uri.getPathSegments().get(0));                
		else if (uriMatcher.match(uri) == BY_COLLECTION_ID)
			//---if getting a particular collection---
			sqlBuilder.appendWhere(COLLECTION_ID + " = " + uri.getPathSegments().get(1));                
		else if (uriMatcher.match(uri) == BY_SERVER_ID)
			//---if getting a particular server---
			sqlBuilder.appendWhere(COLLECTION_ID + " IN (SELECT " + DavCollections._ID + 
                    " FROM " + DavCollections.DATABASE_TABLE +
                    " WHERE " + DavCollections.SERVER_ID + " = " + uri.getPathSegments().get(1) + ")");                

		if (sortOrder==null || sortOrder.equals("") )
			sortOrder = _ID;

		Cursor c = sqlBuilder.query(
				AcalDB, 
				projection, 
				selection, 
				selectionArgs, 
				null, 
				null, 
				sortOrder);

		//---register to watch a content URI for changes---
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int count = 0;
	
		switch (uriMatcher.match(uri)){
		case ALL_RESOURCES:
			count = AcalDB.update(
					DATABASE_TABLE, 
					values,
					selection, 
					selectionArgs);
			break;
		case BY_RESOURCE_ID:                
			count = AcalDB.update(
					DATABASE_TABLE, 
					values,
					_ID + " = " + uri.getPathSegments().get(0) + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
			break;
		case BY_COLLECTION_ID:                
			count = AcalDB.update(
					DATABASE_TABLE, 
					values,
					COLLECTION_ID + " = " + uri.getPathSegments().get(1) + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
			break;
		case BY_SERVER_ID:
			String server_id = uri.getPathSegments().get(1);
			count = AcalDB.update(
					DATABASE_TABLE,
					values,
					COLLECTION_ID + " IN (SELECT " + DavCollections._ID + 
					                      " FROM " + DavCollections.DATABASE_TABLE +
					                      " WHERE " + DavCollections.SERVER_ID + " = " + server_id + ")" + 
					(!TextUtils.isEmpty(selection) ? " AND (" + 
							selection + ')' : ""), 
							selectionArgs);
			break;
		case BEGIN_TRANSACTION:	//Return 1 for success or 0 for failure
			//We are beginning a new transaction only (at this time we wont allow nested tx's)
			if (AcalDB.inTransaction()) return 0;
			AcalDB.beginTransaction();
			return 1;

	case END_TRANSACTION:	//Return 1 for success or 0 for failure
		//We are ending an existing transaction only
		if (!AcalDB.inTransaction()) return 0;
		AcalDB.endTransaction();
		return 1;
	case APPROVE_TRANSACTION:	//Return 1 for success or 0 for failure
		//We are ending an existing transaction only
		if (!AcalDB.inTransaction()) return 0;
		AcalDB.setTransactionSuccessful();
		return 1;
		default: throw new IllegalArgumentException(
				"Unknown URI " + uri);    
		}       
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	
	/**
	 * Static method to retrieve a particular database row for a given collectionId & resource name.
	 * @param collectionId
	 * @param name
	 * @param contentResolver
	 * @return A ContentValues which is the dav_resource row, or null
	 */
	public static ContentValues getResourceInCollection(long collectionId, String name, ContentResolver contentResolver) {
		ContentValues resourceData = null;
		Cursor c = null;
		try {
			c = contentResolver.query(Uri.withAppendedPath(CONTENT_URI,"collection/"+Long.toString(collectionId)),
						null, RESOURCE_NAME+"=?", new String[] { name }, null);
			if ( !c.moveToFirst() ) {
				if ( Constants.LOG_DEBUG )
					Log.d(TAG, "No dav_resource row for collection " + Long.toString(collectionId)+", "+name);
				c.close();
				return null;
			}
			resourceData = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(c,resourceData);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting server data from DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			c.close();
			return null;
		}
		finally {
			c.close();
		}
		return resourceData;
	}

	/**
	 * Static method to retrieve a particular database row for a given resource ID.
	 * @param resourceId
	 * @param contentResolver
	 * @return A ContentValues which is the dav_resource row, or null
	 */
	public static ContentValues getRow(Integer resourceId, ContentResolver contentResolver) {
		ContentValues resourceData = null;
		Cursor c = null;
		try {
			c = contentResolver.query(Uri.withAppendedPath(CONTENT_URI,Long.toString(resourceId)),
						null, null, null, null);
			if ( !c.moveToFirst() ) {
				if ( Constants.LOG_DEBUG )
					Log.d(TAG, "No dav_resource row for collection " + Long.toString(resourceId));
				c.close();
				return null;
			}
			resourceData = new ContentValues();
			DatabaseUtils.cursorRowToContentValues(c,resourceData);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting dav_resources data from DB: " + e.getMessage());
			Log.e(TAG, Log.getStackTraceString(e));
			c.close();
			return null;
		}

		c.close();

		return resourceData;
	}
}
