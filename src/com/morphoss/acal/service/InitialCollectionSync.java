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

package com.morphoss.acal.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.DatabaseChangedEvent;
import com.morphoss.acal.HashCodeUtil;
import com.morphoss.acal.ResourceModification;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.AcalDBHelper;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.SynchronisationJobs.WriteActions;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.xml.DavNode;

public class InitialCollectionSync extends ServiceJob {

	private int collectionId = -2;
	private int serverId = -2;
	private String collectionPath = null;
	ContentValues collectionValues = null;
	private boolean isCollectionIdAssigned = false;
	private boolean collectionNeedsSync = false;

	private AcalRequestor 		requestor;
	
	private static final String TAG = "aCal InitialCollectionSync";
	private ContentResolver cr;
	public static final int MAX_RESULTS = 100;

	private Header[] syncHeaders = new Header[] {
			new BasicHeader("Depth","1"),
			new BasicHeader("Content-Type","text/xml; encoding=UTF-8")
	};
	
	private final String syncData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
										"<sync-collection xmlns=\"DAV:\">"+
											"<sync-token/>"+
											"<prop>"+
												"<getetag/>"+
											"</prop>"+
/**											"<limit>"+
												"<nresults>"+MAX_RESULTS+"</nresults>"+
											"</limit>"+
*/
										"</sync-collection>";
	private aCalService	context;
												
	private final String calendarQuery = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
	"<calendar-query xmlns:D=\"DAV:\" xmlns=\"urn:ietf:params:xml:ns:caldav\">\n"+
	"  <D:prop>\n"+
	"   <D:getetag/>\n"+
	"   <calendar-data/>\n"+
	"   <D:getlastmodified/>\n"+
	"   <D:getcontenttype/>\n"+
	"  </D:prop>\n"+
	"  <filter>\n"+
	"    <comp-filter name=\"VCALENDAR\">\n"+
	"      <comp-filter name=\"VEVENT\">\n"+
	"        <time-range start=\"%s\" end=\"%s\"/>\n"+
	"      </comp-filter>\n"+
	"    </comp-filter>\n"+
	"  </filter>\n"+
	"</calendar-query>";


	public InitialCollectionSync (int collectionId ) {
		this.collectionId = collectionId;
		this.isCollectionIdAssigned = true;
		collectionPath = null;
	}
	
	public InitialCollectionSync (int collectionId, int serverId, String collectionPath) {
		this.collectionId = collectionId;
		this.serverId = serverId;
		this.collectionPath = collectionPath;
		if ( collectionPath == null || serverId < 0 || collectionId < 0 )
			throw new IllegalArgumentException("collectionPath, serverId and collectionId should be assigned real values!");
		this.isCollectionIdAssigned = true;
	}

	public InitialCollectionSync ( int serverId, String collectionPath) {
		this.serverId = serverId;
		this.collectionPath = collectionPath;
	}
	
	
	@Override
	public void run(aCalService context) {
		this.context = context;
		this.cr = context.getContentResolver();
		
		if ( !getCollectionId() ) return;
		
		collectionNeedsSync = false;

		if (Constants.LOG_DEBUG) Log.d(TAG, "Starting initial sync process for server " + serverId + ", Collection: " + collectionPath);
		ContentValues serverData;
		try {
			// get serverData
			serverData = Servers.getRow(serverId, cr);
			if (serverData == null) throw new Exception("No record for ID " + serverId);
			requestor = AcalRequestor.fromServerValues(serverData);
			requestor.interpretUriString(collectionPath);
		}
		catch (Exception e) {
			// Error getting data
			Log.e(TAG, "Error getting server data: " + e.getMessage());
			Log.e(TAG, "Deleting invalid collection Record.");
			cr.delete(Uri.withAppendedPath(DavCollections.CONTENT_URI,Long.toString(collectionId)), null, null);
			return;
		}
		
		if ( null == serverData.getAsInteger(Servers.HAS_SYNC) || 0 == serverData.getAsInteger(Servers.HAS_SYNC) ) {
			Log.i(TAG, "Skipping initial sync process since server does not support WebDAV synchronisation");
			collectionNeedsSync = true;

			if ( collectionValues.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1 ) {
				AcalDBHelper dbHelper = new AcalDBHelper(this.context);
				SQLiteDatabase db = dbHelper.getWritableDatabase();
				syncRecentEvents(db);
				db.close();
			}
		}
		else {
	
			DavNode root = requestor.doXmlRequest("REPORT", collectionPath, syncHeaders, syncData);
			if (requestor.getStatusCode() == 404) {
				Log.i(TAG, "Sync REPORT got 404 on " + collectionPath + " so a HomeSetsUpdate is being scheduled.");
				ServiceJob sj = new HomeSetsUpdate(serverId);
				context.addWorkerJob(sj);
				return;
			}
			if ( root == null ) {
				Log.w(TAG, "Unable to do intial sync - no XML data from server.");
				collectionNeedsSync = true;
			}
			else {
				processSyncToDatabase(root);
			}
		}

		// Now schedule a sync contents on this.
		context.addWorkerJob(new SyncCollectionContents(collectionId,collectionNeedsSync));
	}
	

	private boolean getCollectionId() {
		Cursor cursor = null;
		try {
			if ( collectionPath == null ) {
				collectionValues = DavCollections.getRow(collectionId, cr);
				if ( collectionValues != null ) {
					serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
					collectionPath = collectionValues.getAsString(DavCollections.COLLECTION_PATH);
					isCollectionIdAssigned = true;
				}
				else {
					Log.e(TAG,"Cannot find collection ID "+collectionId+" which I should sync!");
				}
			}
			else if ( serverId > 0 && collectionId < 0 ) {
				cursor = cr.query(DavCollections.CONTENT_URI, null, 
							DavCollections.SERVER_ID + "=? AND " + DavCollections.COLLECTION_PATH + "=?",
							new String[] { "" + serverId, collectionPath }, null);
				if ( cursor.moveToFirst() ) {
					collectionValues = new ContentValues();
					DatabaseUtils.cursorRowToContentValues(cursor, collectionValues);
					collectionId = collectionValues.getAsInteger(DavCollections._ID);
					isCollectionIdAssigned = true;
				}
				else {
					Log.e(TAG,"Cannot find collection "+collectionPath+" which I should sync!");
				}
			}
			else if ( collectionId < 0 ) {
				Log.e(TAG,"Cannot find collection which I should sync!");
				throw new IllegalStateException();
			}
		}
		catch ( Exception e ) {
			Log.e(TAG,"Error finding "+collectionPath+" in database: " + e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if (cursor != null) cursor.close();
		}
		return ( isCollectionIdAssigned && collectionPath != null );
	}

	
	public void processSyncToDatabase( DavNode root ) {
		AcalDBHelper dbHelper = new AcalDBHelper(this.context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		Cursor resourceCursor;
		try {
		
			// begin transaction
			if (Constants.LOG_VERBOSE) Log.v(TAG, "DavResources DB Transaction started.");

			// Get a map of all existing records where Name is the key.
			resourceCursor = db.query(DavResources.DATABASE_TABLE,
						null,
						DavResources.COLLECTION_ID+"=?", new String[] { Integer.toString(collectionId) },
						null, null, null);
			resourceCursor.moveToFirst();
			ContentQueryMap cqm = new ContentQueryMap(resourceCursor, DavResources.RESOURCE_NAME, false, null);
			cqm.requery();
			Map<String, ContentValues> databaseList = cqm.getRows();
			cqm.close();			
			resourceCursor.close();

			Map<String, ContentValues> serverList = new HashMap<String,ContentValues>();
			List<DavNode> responseList = root.getNodesFromPath("multistatus/response");

			if ( Constants.LOG_VERBOSE ) {
				Log.v(TAG,"Database list has "+databaseList.size()+" rows.");
				Log.v(TAG,"Response list has "+responseList.size()+" rows.");
			}
			
			// This could potentially be a really big list of small response sections, so
			// we allocate variables here and re-use inside loop for performance.
			List<DavNode> propstats;
			String name;
			DavNode prop = null;

			//iterate through each response and add to serverList
			for (DavNode response : responseList) {
				//get each prop
				prop = null;
				propstats = response.getNodesFromPath("propstat");
				name = response.segmentFromFirstHref("href");
				for (DavNode propstat : propstats) {
					if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) continue;
					prop = propstat.getNodesFromPath("prop").get(0); 
				}
				if ( name == null || prop == null ) continue;

				ContentValues cv = new ContentValues();
				cv.put(DavResources.COLLECTION_ID, collectionId);
				cv.put(DavResources.RESOURCE_NAME, name);
				cv.put(DavResources.NEEDS_SYNC, 1);

				serverList.put(name, cv);

				//Remove subtree to free up memory
				root.removeSubTree(response);
			}
			//Remove all duplicates
			removeDuplicates(databaseList, serverList);

			List<ResourceModification> changeList = new ArrayList<ResourceModification>(databaseList.size());
			for ( Entry<String,ContentValues> e : databaseList.entrySet() ) {
				changeList.add(new ResourceModification(WriteActions.DELETE, e.getValue(), null));
			}
			String id;
			ContentValues cv;
			for ( Entry<String,ContentValues> e : serverList.entrySet() ) {
				cv = e.getValue();
				id = cv.getAsString(DavResources._ID);
				changeList.add(new ResourceModification((id == null || id.equals("")
															? WriteActions.INSERT
															: WriteActions.UPDATE),
							cv, null));
			}

			db.beginTransaction();

			boolean successful = ResourceModification.applyChangeList(db,changeList); 

			if ( root != null ) {
				//Update sync token
				cv = new ContentValues();
				String syncToken = root.getFirstNodeText("multistatus/sync-token");
				cv.put(DavCollections.SYNC_TOKEN, syncToken);
				db.update(DavCollections.DATABASE_TABLE, cv,
							DavCollections._ID+"=?", new String[] {Integer.toString(collectionId)});
			}
				
			// Finish the transaction, as soon as possible
			if ( successful ) db.setTransactionSuccessful();
			db.endTransaction();

			if (Constants.LOG_VERBOSE)
				Log.v(TAG, databaseList.size() + " records deleted, " + serverList.size() + " updated");			

			if ( collectionValues.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1 ) {
				syncRecentEvents(db);
			}
			
			//We can now approve the transaction
			db.close();
		}
		catch (Exception e) {
			Log.w(TAG, "Initial Sync transaction failed. Data not will not be saved.");
			Log.e(TAG,Log.getStackTraceString(e));
			if ( db.inTransaction() ) db.endTransaction();
			db.close();
		}

		//lastly, create new regular sync
		context.addWorkerJob(new SyncCollectionContents(this.collectionId)); 
	}

	
	/**
	 * When we have a lot of events to sync, we want to make sure that the period
	 * of time around the present day is in sync first.
	 */
	private void syncRecentEvents(SQLiteDatabase db) {
		AcalDateTime from = new AcalDateTime().applyLocalTimeZone().addDays(-32).shiftTimeZone("UTC");
		AcalDateTime until = new AcalDateTime().applyLocalTimeZone().addDays(+68).shiftTimeZone("UTC");

		if (Constants.LOG_DEBUG)
			Log.d(TAG, "Doing a recent sync of events from "+from.toString()+" to "+until.toString());			
		
		DavNode root = requestor.doXmlRequest("REPORT", collectionPath, SynchronisationJobs.getReportHeaders(1),
					String.format(calendarQuery, from.fmtIcal(), until.fmtIcal()));

		ArrayList<ResourceModification> changeList = new ArrayList<ResourceModification>(); 

		List<DavNode> responses = root.getNodesFromPath("multistatus/response");

		for (DavNode response : responses) {
			String name = response.segmentFromFirstHref("href");
			ContentValues cv = null;
			Cursor c = db.query(DavResources.DATABASE_TABLE, null,
						DavResources.COLLECTION_ID+"=? AND "+DavResources.RESOURCE_NAME+"=?",
						new String[] {Integer.toString(collectionId), name}, null, null, null);
			if ( c.moveToFirst() ) {
				cv = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, cv);
			}
			c.close();
			
			WriteActions action = WriteActions.UPDATE;
			if ( cv == null ) {
				cv = new ContentValues();
				cv.put(DavResources.COLLECTION_ID, collectionId);
				cv.put(DavResources.RESOURCE_NAME, name);
				cv.put(DavResources.NEEDS_SYNC, 1);
				action = WriteActions.INSERT;
			}
			if ( !parseResponseNode(response, cv) ) continue;
			
			changeList.add( new ResourceModification(action,cv,null));
		}
		
		ResourceModification.commitChangeList(context, changeList);
		
	}

	
	/**
	 * <p>
	 * Parse a single &lt;response&gt; node within a &lt;multistatus&gt;
	 * </p>
	 * @return true If we need to write to the database, false otherwise.
	 */
	private boolean parseResponseNode(DavNode responseNode, ContentValues cv) {

		List<DavNode> propstats = responseNode.getNodesFromPath("propstat");
		if ( propstats.size() < 1 ) return false;

		for (DavNode propstat : propstats) {
			if ( !propstat.getFirstNodeText("status").equalsIgnoreCase("HTTP/1.1 200 OK") ) {
				responseNode.removeSubTree(propstat);
				continue;
			}

			DavNode prop = propstat.getNodesFromPath("prop").get(0);

			String etag = prop.getFirstNodeText("getetag");
			String data = prop.getFirstNodeText("calendar-data");
			String last_modified = prop.getFirstNodeText("getlastmodified");
			String content_type = prop.getFirstNodeText("getcontenttype");
			if ( etag == null || data == null ) return false;
			
			String oldEtag = cv.getAsString(DavResources.ETAG);
			
			if ( oldEtag != null && oldEtag.equals(etag) ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"ETag matches existing record, so no need to sync.");
				cv.put(DavResources.NEEDS_SYNC, 0 );
				return false;
			}

			if ( last_modified != null ) cv.put(DavResources.LAST_MODIFIED, last_modified);
			if ( content_type != null ) cv.put(DavResources.CONTENT_TYPE, content_type);
			if ( data != null ) {
				cv.put(DavResources.RESOURCE_DATA, data); 
				cv.put(DavResources.ETAG, etag);
				cv.put(DavResources.NEEDS_SYNC, 0 );
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Got data now, so no need to sync later.");
				return true;
			}
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Need to sync "+cv.getAsString(DavResources.RESOURCE_NAME));
			cv.put(DavResources.NEEDS_SYNC, 1 );
		}

		// We remove our references to this now, since we've finished with it.
		responseNode.getParent().removeSubTree(responseNode);

		return true;
	}

	
	public boolean equals(Object that) {
		if ( this == that ) return true;
	    if ( !(that instanceof InitialCollectionSync) ) return false;
	    InitialCollectionSync thatCis = (InitialCollectionSync)that;
	    if ( this.collectionPath == null && thatCis.collectionPath == null ) return true;
	    if ( this.collectionPath == null || thatCis.collectionPath == null ) return false;
	    return (
	    	this.collectionPath.equals(thatCis.collectionPath) &&
	    	this.serverId == thatCis.serverId
	    	);
		
	}
	
	public int hashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash( result, this.serverId );
	    result = HashCodeUtil.hash( result, this.collectionPath );
	    return result;
	}
	
	private void removeDuplicates(Map<String,ContentValues> db, Map<String,ContentValues> server) {
		String[] names = new String[server.size()];
		server.keySet().toArray(names);
		for (String name : names) {
			if (!db.containsKey(name)) continue;	//New value from server
			if ( db.get(name).getAsString(DavResources.ETAG) != null && server.get(name).getAsString(DavResources.ETAG) != null
					&& db.get(name).getAsString(DavResources.ETAG).equals(server.get(name).getAsString(DavResources.ETAG)))  {
				//records match, remove from both
				server.remove(name);
			}
			else {
				server.get(name).put(DavResources._ID, db.get(name).getAsString(DavResources._ID));	//record to be updated. Insert ID
			}
			db.remove(name);
		}
	}
	
	public int deleteRecords(SQLiteDatabase db, Map<String,ContentValues> toDelete) {
		//database list now contains all the records we need to delete
		String delIds = "";
		boolean sep = false;
		String names[] = new String[toDelete.size()];
		toDelete.keySet().toArray(names);
		for (String name : names) {
			if (sep)delIds+=",";
			else sep=true;
			delIds+=toDelete.get(name).getAsInteger(DavResources._ID);

			aCalService.databaseDispatcher.dispatchEvent(new DatabaseChangedEvent(
						DatabaseChangedEvent.DATABASE_RECORD_DELETED, DavResources.class, toDelete.get(name)));
		}
		
		if (delIds == null || delIds.equals("")) return 0;
		
		//remove from db
		return db.delete( DavResources.DATABASE_TABLE, DavResources._ID+" IN ("+delIds+")", new String[] {  });
	}
	
/*
	public int updateRecords(SQLiteDatabase db, Map<String, ContentValues> toUpdate) {
		String names[] = new String[toUpdate.size()];
		toUpdate.keySet().toArray(names);
		for (String name : names) {
			ContentValues cv = toUpdate.get(name);
			cv.put(DavResources.NEEDS_SYNC, 1);
			cv.put(DavResources.COLLECTION_ID, this.collectionId);
			// is this an update or insert?
			String id = cv.getAsString(DavResources._ID);
			WriteActions action;
			if (id == null || id.equals("")) {
				action = WriteActions.INSERT;
			}
			else {
				action = WriteActions.UPDATE;
			}

			SynchronisationJobs.writeResource(db,action,cv);
			collectionNeedsSync = true;
			db.yieldIfContendedSafely();
		}
		return names.length;
	}
*/
	
	@Override
	public String getDescription() {
		return "Initial collection sync on "
					+(serverId>-1?"server "+serverId:"collection "+collectionId)
					+(collectionPath==null?"":", path "+collectionPath);
	}

}
