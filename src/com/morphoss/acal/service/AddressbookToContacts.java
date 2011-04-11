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
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.ContentProviderOperation.Builder;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.Data;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.AcalProperty;
import com.morphoss.acal.davacal.VCard;
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
	private String collectionAccountName;
	private String acalAccountType;
	private AccountManager accountManager;
	private Account[]	accountList;
	

	private static final Pattern structuredAddressMatcher = Pattern.compile("^(.*);(.*);(.*);(.*);(.*);(.*);(.*)$");
	private static final Pattern structuredNameMatcher = Pattern.compile("^(.*);(.*);(.*);(.*);(.*)$");
	
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
		
		ensureAccountCreated();

		Log.i(TAG,getDescription() + " - " + collectionAccountName);
		updateContactsFromAddressbook();
	}

	
	@Override
	public String getDescription() {
		return "Updating Android Contacts from Addressbook " + collectionId;
	}

	
	private void ensureAccountCreated() {
		accountManager = AccountManager.get(context);
		int serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverValues = Servers.getRow(serverId, cr);
		collectionAccountName = serverValues.getAsString(Servers.FRIENDLY_NAME)
								+", " + collectionValues.getAsString(DavCollections.DISPLAYNAME);

		accountList = accountManager.getAccountsByType(acalAccountType);
		
		boolean found = false;
		for( Account a : accountList ) {
			String tmpString = accountManager.getUserData(a, AcalAuthenticator.SERVER_ID );
			if ( tmpString == null || !tmpString.equals(Integer.toString(serverId)) ) continue;

			tmpString = accountManager.getUserData(a, AcalAuthenticator.COLLECTION_ID );
			if ( tmpString == null || !tmpString.equals(Integer.toString(collectionId)) ) continue;

			found = true;
		}

		if ( !found ) {
			Account newAccount = new Account(collectionAccountName, acalAccountType );
			Bundle userData = new Bundle();
			userData.putString(AcalAuthenticator.SERVER_ID, serverValues.getAsString(Servers._ID));
			userData.putString(AcalAuthenticator.COLLECTION_ID, collectionValues.getAsString(DavCollections._ID));
			userData.putString(AcalAuthenticator.USERNAME, serverValues.getAsString(Servers.USERNAME));
			accountManager.addAccountExplicitly(newAccount, serverValues.getAsString(Servers.PASSWORD), userData);
		}
	}

	private void updateContactsFromAddressbook() {
		ContentValues[] vCards = fetchVCards();
		
		for( ContentValues vCardRow : vCards ) {
			try {
				VCard vCard = (VCard) VCard.createComponentFromResource(vCardRow, addressBookCollection);
				AcalProperty uidProperty = vCard.getProperty("UID");
				if ( uidProperty == null )
					uidProperty = new AcalProperty("UID",vCardRow.getAsString(DavResources._ID));

				Cursor cur = cr.query(ContactsContract.RawContacts.CONTENT_URI, null,
			                RawContacts.ACCOUNT_TYPE+"=? AND " + RawContacts.SYNC1+"=?",
			                new String[] { acalAccountType, uidProperty.getValue() },
							null);

				if ( cur.getCount() > 1 ) {
					throw new IllegalStateException("Found "+cur.getCount()+" RawContact rows for "+uidProperty.toRfcString());
				}
				else if ( cur.getCount() < 1 ) {
					contactFromVCard( vCard, -1 );
				}
				else {
				    while (cur.moveToNext()) {
				        String id = cur.getString(
			                        cur.getColumnIndex(Contacts._ID));
				        String name = cur.getString(
			                        cur.getColumnIndex(Contacts.DISPLAY_NAME));
				        Log.d(TAG,"Found existing contact row for '"+name+"' ("+id+")");
			        }
			 	}

			}
			catch (VComponentCreationException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}
	}

	
	private void contactFromVCard(VCard vCard, int rawContactId) {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		if ( rawContactId < 0 ) {

			Log.d(TAG,"Inserting data for '"+vCard.getProperty("FN").getValue()+"'");
			int rawContactInsertIndex = ops.size();
			ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
						.withValue(RawContacts.ACCOUNT_TYPE, acalAccountType)
						.withValue(RawContacts.ACCOUNT_NAME, collectionAccountName)
						.build());

			String propertyName;
			AcalProperty[] vCardProperties = vCard.getAllProperties();
			for( AcalProperty prop : vCardProperties ) {
				Builder op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
				op.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex);
				propertyName = prop.getName();
				if ( propertyName.equals("FN") )
					doStructuredName(op, prop, vCard.getProperty("N") );
				else if ( propertyName.equals("TEL") )
					doPhone(op, prop );
				else if ( propertyName.equals("ADR") )
					doStructuredAddress(op, prop );
				else if ( propertyName.equals("EMAIL") )
					doEmail(op, prop );
				else
					continue;

				ops.add(op.build());
			}
			try {
				cr.applyBatch(ContactsContract.AUTHORITY, ops);
			}
			catch (RemoteException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,Log.getStackTraceString(e));
			}
			catch (OperationApplicationException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,Log.getStackTraceString(e));
			}

		}
		
	}


	private void doEmail(Builder op, AcalProperty emailProp) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
		op.withValue(CommonDataKinds.Email.DATA,emailProp.getValue());
		String emailType = emailProp.getParam("TYPE").toUpperCase(); 
		if ( emailType.contains("HOME") ) {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME);
//			op.withValue(CommonDataKinds.Email.LABEL, context.getString(R.string.HomeEmail_label));
		}
		else if ( emailType.contains("WORK") ) {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK);
//			op.withValue(CommonDataKinds.Email.LABEL, context.getString(R.string.WorkEmail_label));
		}
		else {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_OTHER);
//			op.withValue(CommonDataKinds.Email.LABEL, context.getString(R.string.OtherEmail_label));
		}
		Log.v(TAG,"Processing field EMAIL:"+emailType+":"+emailProp.getValue());
		
	}


	private void doStructuredAddress(Builder op, AcalProperty adrProp) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
		String addressType = adrProp.getParam("TYPE").toUpperCase(); 
		if ( addressType.contains("HOME") ) {
			op.withValue(CommonDataKinds.StructuredPostal.TYPE, CommonDataKinds.StructuredPostal.TYPE_HOME);
//			op.withValue(CommonDataKinds.StructuredPostal.LABEL, context.getString(R.string.HomeAddress_label));
		}
		else if ( addressType.contains("WORK") ) {
			op.withValue(CommonDataKinds.StructuredPostal.TYPE, CommonDataKinds.StructuredPostal.TYPE_WORK);
//			op.withValue(CommonDataKinds.StructuredPostal.LABEL, context.getString(R.string.WorkAddress_label));
		}
		else {
			op.withValue(CommonDataKinds.StructuredPostal.TYPE, CommonDataKinds.StructuredPostal.TYPE_OTHER);
//			op.withValue(CommonDataKinds.StructuredPostal.LABEL, context.getString(R.string.OtherAddress_label));
		}
		Log.v(TAG,"Processing field ADR:"+addressType+":"+adrProp.getValue());

		Matcher m = structuredAddressMatcher.matcher(adrProp.getValue());
		if ( m.matches() ) {
			/**
			 * The structured type value
			 * corresponds, in sequence, to the post office box; the extended
			 * address (e.g. apartment or suite number); the street address; the
			 * locality (e.g., city); the region (e.g., state or province); the
			 * postal code; the country name.
			 */
			op.withValue(CommonDataKinds.StructuredPostal.POBOX, m.group(1));
			if ( m.group(2) == null || m.group(2).equals("") )
				op.withValue(CommonDataKinds.StructuredPostal.STREET, m.group(3));
			else
				op.withValue(CommonDataKinds.StructuredPostal.STREET, m.group(2) + " / " + m.group(3));
				
			op.withValue(CommonDataKinds.StructuredPostal.CITY, m.group(4));
			op.withValue(CommonDataKinds.StructuredPostal.REGION, m.group(5));
			op.withValue(CommonDataKinds.StructuredPostal.POSTCODE, m.group(6));
			op.withValue(CommonDataKinds.StructuredPostal.COUNTRY, m.group(7));
		}
	}


	private void doPhone(Builder op, AcalProperty telProp ) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		op.withValue(CommonDataKinds.Phone.NUMBER,telProp.getValue());
		String phoneType = telProp.getParam("TYPE").toUpperCase(); 
		if ( phoneType.contains("HOME") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_HOME);
//			op.withValue(CommonDataKinds.Phone.LABEL, context.getString(R.string.HomePhone_label));
		}
		else if ( phoneType.contains("WORK") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK);
//			op.withValue(CommonDataKinds.Phone.LABEL, context.getString(R.string.WorkPhone_label));
		}
		else if ( phoneType.contains("CELL") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE);
//			op.withValue(CommonDataKinds.Phone.LABEL, context.getString(R.string.CellPhone_label));
		}
		else {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_OTHER);
//			op.withValue(CommonDataKinds.Phone.LABEL, context.getString(R.string.OtherPhone_label));
		}
		Log.v(TAG,"Processing field TEL:"+phoneType+":"+telProp.getValue());
	}


	private void doStructuredName(Builder op, AcalProperty fnProp, AcalProperty nProp) {
		Log.v(TAG,"Processing field FN:"+fnProp.getValue());

		op.withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
		if ( nProp != null ) {
			Matcher m = structuredNameMatcher.matcher(nProp.getValue());
			if ( m.matches() ) {
				/**
				 * The structured property value corresponds, in
				 * sequence, to the Surname (also known as family name), Given Names,
				 * Honorific Prefixes, and Honorific Suffixes.
				 */
				op.withValue(CommonDataKinds.StructuredName.FAMILY_NAME, m.group(1));
				op.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, m.group(2));
				op.withValue(CommonDataKinds.StructuredName.PREFIX, m.group(3));
				op.withValue(CommonDataKinds.StructuredName.SUFFIX, m.group(4));
				Log.v(TAG,"Processing 'N' field: '"+nProp.getValue()+"' prefix>"
							+ m.group(3) + "< firstname> " + m.group(2) + "< lastname>" + m.group(1) + "< suffix>" + m.group(4));
			}
		}

		op.withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, fnProp.getValue());
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
