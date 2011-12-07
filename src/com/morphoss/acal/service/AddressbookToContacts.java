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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.contacts.VCardContact;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.Servers;

public class AddressbookToContacts extends ServiceJob {

	private static final String TAG = "aCal AddressBookToContacts";
	private int collectionId;
	private aCalService context;
	private ContentResolver cr;
	private ContentValues collectionValues;
	private AcalCollection addressBookCollection;
	private String acalAccountType;
	private Account	account;
	

	public AddressbookToContacts(int collectionId) {
		this.collectionId = collectionId;
	}

	
	@Override
	public void run(aCalService context) {
		this.context = context;
		this.cr = context.getContentResolver();
		this.acalAccountType = context.getString(R.string.AcalAccountType);
		this.collectionValues = DavCollections.getRow(collectionId, cr);
		this.addressBookCollection = new AcalCollection(collectionValues);
		
		this.account = getAndroidAccount();
		if ( account == null ) {
			Log.println(Constants.LOGD, TAG, "Addressbook "+collectionId+" '"+collectionValues.getAsString(DavCollections.DISPLAYNAME)+"' not marked for synchronisation to Contacts.");
			return;
		}

		Log.i(TAG,getDescription() + ": " + account.name);
		updateContactsFromAddressbook();
	}

	
	@Override
	public String getDescription() {
		return "Updating Android Contacts from Addressbook " + collectionId;
	}

	
	private Account getAndroidAccount() {
		AccountManager accountManager = AccountManager.get(context);
		int serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverValues = Servers.getRow(serverId, cr);
		String collectionAccountName = serverValues.getAsString(Servers.FRIENDLY_NAME)
								+" - " + collectionValues.getAsString(DavCollections.DISPLAYNAME);

		Account[] accountList = accountManager.getAccountsByType(acalAccountType);

		int i=0;
		while ( i < accountList.length && !accountList[i].name.equals(collectionAccountName) ) {
			i++;
		}
		if ( i < accountList.length ) return accountList[i];
		return null;
	}

	
	private void updateContactsFromAddressbook() {
		ContentValues[] vCards = fetchVCards();
		
		for( ContentValues vCardRow : vCards ) {
			try {
				VCardContact vCard = new VCardContact(vCardRow, addressBookCollection);

				Cursor cur = cr.query(ContactsContract.RawContacts.CONTENT_URI, null,
			                RawContacts.ACCOUNT_TYPE+"=? AND " + RawContacts.SYNC1+"=?",
			                new String[] { acalAccountType, vCard.getUid() },
							null);

				if ( cur == null ) {
					throw new NullPointerException("Unable to query database");
				}
				else if ( cur.getCount() > 1 ) {
					throw new IllegalStateException("Found "+cur.getCount()+" RawContact rows for "+vCard.getUid());
				}
				else if ( cur.getCount() < 1 ) {
					vCard.writeToContact(context, account, -1L );
				}
				else {
				    while (cur.moveToNext()) {
				        String id = cur.getString( cur.getColumnIndex(Contacts._ID));
				        String name = cur.getString( cur.getColumnIndex(Contacts.DISPLAY_NAME));
				        Log.println(Constants.LOGD,TAG,"Found existing contact row for '"+name+"' ("+id+")");
			        }
			 	}

			}
			catch (VComponentCreationException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}
	}

	
	/**
	 * Fetch the VCards we should be looking at.
	 * @return an array of String
	 */
	private ContentValues[] fetchVCards() {
		Cursor mCursor = null;
		ContentValues vcards[] = null;

		if (Constants.LOG_VERBOSE) Log.v(TAG, "Retrieving VCards" );
		try {
			Uri vcardResourcesUri = Uri.parse(DavResources.CONTENT_URI.toString()+"/collection/"+this.collectionId);
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
