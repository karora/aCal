package com.morphoss.acal.service;

import java.util.HashMap;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.contacts.VCardContact;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.providers.DavResources;

public class ContactsSyncAdapterService extends Service {
	private static final String		TAG					= "ContactsSyncAdapterService";
	private static SyncAdapterImpl	sSyncAdapter		= null;
	private static ContentResolver	mContentResolver	= null;

	public ContactsSyncAdapterService() {
		super();
	}

	private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
		private Context	mContext;

		public SyncAdapterImpl(Context context) {
			super(context, true);
			mContext = context;
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
				SyncResult syncResult) {
			try {
				ContactsSyncAdapterService.performSync(mContext, account, extras, authority, provider, syncResult);
			}
			catch ( OperationCanceledException e ) {
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder ret = null;
		ret = getSyncAdapter().getSyncAdapterBinder();
		return ret;
	}

	private SyncAdapterImpl getSyncAdapter() {
		if ( sSyncAdapter == null ) sSyncAdapter = new SyncAdapterImpl(this);
		return sSyncAdapter;
	}

	private static void performSync(Context context, Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncResult) throws OperationCanceledException {
		HashMap<String, Long> androidContacts = new HashMap<String, Long>();
		mContentResolver = context.getContentResolver();
		Log.i(TAG, "performSync: " + account.toString());
		
		 // Load the local contacts for this account
		Uri rawContactUri = RawContacts.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type).build();
		Cursor c1 = mContentResolver.query(rawContactUri, new String[] { BaseColumns._ID, RawContacts.SYNC1 }, null, null, null);
		while ( c1.moveToNext() ) {
			androidContacts.put(c1.getString(1), c1.getLong(0));
		}

		long collectionId = Long.parseLong(AccountManager.get(context).getUserData(account, AcalAuthenticator.COLLECTION_ID));
		AcalCollection collection = AcalCollection.fromDatabase(context, collectionId);
		if ( collection == null ) {
			return;
		}
		ContentValues[] davCardRows = fetchVCards(mContentResolver,collectionId);
		for ( ContentValues cardRow : davCardRows ) {
			VCardContact vc;
			try {
				vc = new VCardContact(cardRow,collection);
			}
			catch ( VComponentCreationException e ) {
				Log.println(Constants.LOGD, TAG, "Could not make VCard from resource ID "+cardRow.getAsString(DavResources._ID));
				Log.println(Constants.LOGV, TAG, cardRow.getAsString(DavResources.RESOURCE_DATA));
				continue;
			}
			Long androidContactId = androidContacts.get(vc.getUid());
			if ( androidContactId == null ) {
				vc.writeToContact(context, account, -1L);
			}
			else {
				Integer aCalSequence = vc.getSequence();
				ContentValues androidContact = VCardContact.getAndroidContact(context,androidContactId);
				int androidSequence = androidContact.getAsInteger(RawContacts.VERSION);
				if ( aCalSequence == null || aCalSequence < androidSequence ) {
					vc.writeToVCard(context, androidContact);
				}
				else if ( aCalSequence == androidSequence ) {
					Log.println(Constants.LOGD, TAG, "Records are in sync");
				}
				else {
					vc.writeToContact(context, account, androidContactId);
				}
				androidContacts.remove(vc.getUid());
			}
		}
		
		/**
		 * @todo: Here we should go through any remaining androidContacts and create the VCards
		 * for them.
		 */
	}


	/**
	 * Fetch the VCards we should be looking at.
	 * @return an array of String
	 */
	private static ContentValues[] fetchVCards(ContentResolver cr, long collectionId) {
		Cursor mCursor = null;
		ContentValues vcards[] = null;

		if (Constants.LOG_VERBOSE) Log.v(TAG, "Retrieving VCards" );
		try {
			Uri vcardResourcesUri = Uri.parse(DavResources.CONTENT_URI.toString()+"/collection/"+collectionId);
			mCursor = cr.query(vcardResourcesUri, null, null, null, null);
			vcards = new ContentValues[mCursor.getCount()];
			int count = 0;
			for( mCursor.moveToFirst(); !mCursor.isAfterLast(); mCursor.moveToNext()) {
				ContentValues newCard = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(mCursor, newCard);
				vcards[count++] = newCard;
			}
		}
		catch (Exception e) {
			Log.e(TAG,"Unknown error retrieving VCards: "+e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		finally {
			if (mCursor != null) mCursor.close();
		}
		if (Constants.LOG_VERBOSE)
			Log.v(TAG, "Retrieved " + (vcards == null ? 0 :vcards.length) + " VCard resources.");
		return vcards;
	}
	
}
