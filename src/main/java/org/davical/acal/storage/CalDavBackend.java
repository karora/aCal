/*
 * Copyright (C) 2026 Andrew McMillan
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

package org.davical.acal.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentValues;
import android.util.Log;

import org.davical.acal.Constants;
import org.davical.acal.database.DMAction;
import org.davical.acal.database.DMQueryBuilder;
import org.davical.acal.database.TableManager.QUERY_ACTION;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.providers.Servers;
import org.davical.acal.service.HomeSetDiscovery;
import org.davical.acal.service.HomeSetsUpdate;
import org.davical.acal.service.InitialCollectionSync;
import org.davical.acal.service.ServiceJob;
import org.davical.acal.service.SyncCollectionContents;
import org.davical.acal.service.connector.AcalRequestor;
import org.davical.acal.service.connector.ConnectionFailedException;
import org.davical.acal.service.connector.SendRequestFailedException;

/**
 * Collections kept on a CalDAV or CardDAV server.
 */
public class CalDavBackend implements StorageBackend {

	public static final String	TAG		= "aCal CalDavBackend";

	private static boolean DEBUG = true && Constants.DEBUG_MODE;

	private AcalRequestor requestor;

	private AcalRequestor requestorFor(WriteableResourceTableManager processor, ContentValues serverData) {
		if ( requestor == null ) requestor = new AcalRequestor();
		Servers.decryptPassword(processor.getContext(), serverData);
		requestor.applyFromServer(serverData);
		return requestor;
	}

	@Override
	public boolean needsNetwork() {
		return true;
	}

	@Override
	public ServiceJob discoverCollections(int serverId) {
		return new HomeSetDiscovery(serverId);
	}

	@Override
	public ServiceJob updateCollections(int serverId) {
		return new HomeSetsUpdate(serverId);
	}

	@Override
	public ServiceJob refreshCollection(long collectionId, boolean now) {
		return new SyncCollectionContents(collectionId, now);
	}

	@Override
	public ServiceJob reloadCollection(long collectionId) {
		return new InitialCollectionSync(collectionId);
	}

	@Override
	public boolean commitPendingChange(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData, ContentValues pending) throws IOException {

		requestorFor(processor, serverData);

		long collectionId = pending.getAsLong(ResourceTableManager.PEND_COLLECTION_ID);
		String collectionPath = collectionData.getAsString(DavCollections.COLLECTION_PATH);

		String newData = pending.getAsString(ResourceTableManager.NEW_DATA);
		String oldData = pending.getAsString(ResourceTableManager.OLD_DATA);
		String latestDbData = pending.getAsString(ResourceTableManager.RESOURCE_DATA);
		Long resourceId = pending.getAsLong(ResourceTableManager.PEND_RESOURCE_ID);
		Long pendingId = pending.getAsLong(ResourceTableManager.PENDING_ID);

		CalDavChange change = CalDavChange.plan(oldData, newData,
					pending.getAsString(ResourceTableManager.RESOURCE_NAME),
					pending.getAsString(ResourceTableManager.UID),
					pending.getAsString(ResourceTableManager.ETAG));

		if ( oldData != null && newData != null && latestDbData != null && oldData.equals(latestDbData) ) {
			newData = mergeAsyncChanges( oldData, latestDbData, newData );
		}

		String method = change.method();
		String path = collectionPath + change.resourceName;

		if ( DEBUG && Constants.LOG_DEBUG ) {
			Log.println(Constants.LOGD, TAG, "Writing resource with "+method+" to "+path
						+", "+change.matchHeader+": "+change.matchValue+"\n"+
						"latestDbData:\n" +
						"=============================\n"+
						latestDbData+
						"============\n"+
						"newData:\n" +
						"=============================\n"+
						newData+
						"============\n"
						);
		}

		Header[] headers = headersFor(change);

		InputStream in = null;
		try {
			in = requestor.doRequest( method, path, headers, newData);
		}
		catch (ConnectionFailedException e) {
			Log.w(TAG,"HTTP Connection failed: "+e.getMessage());
			return false;
		}
		catch (SendRequestFailedException e) {
			Log.w(TAG,"HTTP Request failed: "+e.getMessage());
			return false;
		}

		int status = requestor.getStatusCode();
        Log.println(Constants.LOGI,TAG, method+" got "+status+" response from "+requestor.fullUrl());

        String responseData = null;
        boolean useResponseData = false;
        if ( !change.isDelete ) try {
            responseData = AcalRequestor.convertStreamToString(in);
            useResponseData = change.contentType.equals(CalDavChange.contentTypeOf(responseData));
        }
        catch ( IOException e ) { }

		switch ( CalDavChange.outcomeOf(status) ) {
			case APPLIED:
				ContentValues resourceData = new ContentValues();
				resourceData.put(ResourceTableManager.RESOURCE_ID, resourceId);
				resourceData.put(ResourceTableManager.COLLECTION_ID, collectionId);
				resourceData.put(ResourceTableManager.RESOURCE_NAME, change.resourceName);
				resourceData.put(ResourceTableManager.RESOURCE_DATA, newData);
				resourceData.put(ResourceTableManager.NEEDS_SYNC, 1);
				resourceData.put(ResourceTableManager.ETAG, "unknown etag after PUT before sync");

                for (Header hdr : requestor.getResponseHeaders()) {
					if (hdr.getName().equalsIgnoreCase("ETag")) {
						resourceData.put(ResourceTableManager.ETAG, hdr.getValue());
						resourceData.put(ResourceTableManager.NEEDS_SYNC, 0);
						break;
					}
				}
                if ( responseData != null && useResponseData ) {
                    resourceData.put(ResourceTableManager.RESOURCE_DATA, responseData);
                }

				if ( DEBUG ) Log.println(Constants.LOGD,TAG,
						"Applying resource modification to local database");
				DMQueryBuilder builder = processor.getNewQueryBuilder()
							.setAction(change.isDelete ? QUERY_ACTION.DELETE : QUERY_ACTION.UPDATE);
				builder.setValues(resourceData);
				builder.setWhereClause(ResourceTableManager.RESOURCE_ID + " = "+resourceId);

				DMAction action = builder.build();

				try {
					processor.syncToServer(action, resourceId, pendingId);
				}
				catch (Exception e) {
					Log.w(TAG, action.toString()+": Exception applying resource modification: " + e.getMessage());
					Log.println(Constants.LOGD,TAG, Log.getStackTraceString(e));
				}
				break;

			case ABANDONED:
				Log.w(TAG, "Status " + status + " for " +method+" "+ path + " - giving up on change.");
                Log.i(TAG,"Full server response was:\n"+responseData);
				processor.deletePendingChange(pendingId);
				break;

			case RETRY:
				Log.w(TAG, "Status " + status + " for " +method+" "+ path);
				Log.i(TAG,"Full server response was:\n"+responseData);
				break;
		}

		// The server answered, so look at what it now has as soon as we are done.
		return true;
	}


	/**
	 * @return the headers to send with a change.  A change to a resource we
	 *         have no etag for has no condition to send, and is sent without one.
	 */
	static Header[] headersFor(CalDavChange change) {
		List<Header> headers = new ArrayList<Header>();
		if ( change.matchHeader != null ) headers.add(new BasicHeader(change.matchHeader, change.matchValue));
		headers.add(new BasicHeader("Content-type", change.contentType));
		if ( !change.isDelete ) headers.add(new BasicHeader("Prefer","return=representation"));
		return headers.toArray(new Header[0]);
	}


	private String mergeAsyncChanges(String oldData, String latestDbData, String newData) {
		/**
		 * @todo Around here is where we should handle the case where latestDbData != oldData. We
		 * need to parse out both objects and work out what the differences are between oldData
		 * and newData, and see if we can apply them to latestDbData without them overwriting
		 * differences between oldData and latestDbData...
		 */
		return newData;
	}


	final private static Header[] proppatchHeaders = new Header[] {
		new BasicHeader("Content-Type","text/xml; charset=utf-8")
	};

	final static String baseProppatch = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+
		"<propertyupdate xmlns=\""+Constants.NS_DAV+"\"\n"+
		"    xmlns:ACAL=\""+Constants.NS_ACAL+"\"\n"+
		"    xmlns:ICAL=\""+Constants.NS_ICAL+"\">\n"+
		"<set>\n"+
		"  <prop>\n"+
		"%s\n"+
		"  </prop>\n"+
		" </set>\n"+
		"</propertyupdate>\n";


	/**
	 * @return the text as CDATA.  A "]]>" in it would end the section early
	 *         and let the rest be read as XML, so the section is ended and
	 *         started again in the middle of each one.
	 */
	static String cdata(String text) {
		return "<![CDATA[" + String.valueOf(text).replace("]]>", "]]]]><![CDATA[>") + "]]>";
	}


	@Override
	public void commitCollectionProperties(WriteableResourceTableManager processor, ContentValues serverData,
				ContentValues collectionData) {
	    StringBuilder settings = new StringBuilder();
	    settings.append("<displayname>"+cdata(collectionData.getAsString(DavCollections.DISPLAYNAME))+"</displayname>");
	    String colour = collectionData.getAsString(DavCollections.COLOUR);
	    if ( colour != null ) {
	        try {
	            String rgb = String.format("#%06X", android.graphics.Color.parseColor(colour) & 0xFFFFFF);
	            settings.append("<ACAL:collection-colour>"+rgb+"</ACAL:collection-colour>");
	            // Dead property in the format Apple clients use (#RRGGBBAA), so the
	            // colour is shared with other CalDAV clients and survives any local
	            // rebuild of the collection list.
	            settings.append("<ICAL:calendar-color>"+rgb+"FF</ICAL:calendar-color>");
	        }
	        catch ( IllegalArgumentException e ) {
	            Log.w(TAG,"Not sending unparseable collection colour '"+colour+"' to server.");
	        }
	    }
		String proppatchRequest = String.format(baseProppatch, settings.toString() );

		try {
			requestorFor(processor, serverData);
			requestor.doRequest("PROPPATCH", collectionData.getAsString(DavCollections.COLLECTION_PATH),
						proppatchHeaders, proppatchRequest);

			collectionData.put(DavCollections.SYNC_METADATA, 0);
			processor.updateCollection(collectionData.getAsLong(DavCollections._ID),collectionData);
		}
		catch (Exception e) {
			Log.e(TAG,"Error with proppatch to "+(requestor == null ? "server" : requestor.fullUrl()));
			Log.println(Constants.LOGD,TAG,Log.getStackTraceString(e));
		}

	}
}
