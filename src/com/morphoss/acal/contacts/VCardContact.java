package com.morphoss.acal.contacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.Data;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.davacal.AcalCollection;
import com.morphoss.acal.davacal.AcalProperty;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.VCard;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.VComponentCreationException;
import com.morphoss.acal.davacal.YouMustSurroundThisMethodInTryCatchOrIllEatYouException;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.service.connector.Base64Coder;

public class VCardContact {
	
	public final static String TAG = "aCal VCardContact";

	private static final Pattern structuredAddressMatcher = Pattern.compile("^(.*);(.*);(.*);(.*);(.*);(.*);(.*)$");
	private static final Pattern structuredNameMatcher = Pattern.compile("^(.*);(.*);(.*);(.*);(.*)$");
	private static final Pattern simpleSplit = Pattern.compile("[.]");
	
	private final ContentValues vCardRow;
	private final VCard sourceCard;
	private Map<String,Set<AcalProperty>> typeMap = null;
	private Map<String,Set<AcalProperty>> groupMap = null;
	private AcalProperty uid = null;
	private AcalProperty sequence = null;

	public VCardContact( ContentValues resourceRow, AcalCollection collectionObject ) throws VComponentCreationException {
		vCardRow = resourceRow;
		try {
			sourceCard = (VCard) VComponent.createComponentFromResource(resourceRow, collectionObject);
		}
		catch ( Exception e ) {
			Log.w(TAG,"Could not build VCard from resource", e);
			throw new VComponentCreationException("Could not build VCard from resource", e); 
		}
 
		try {
			sourceCard.setPersistentOn();
		}
		catch ( YouMustSurroundThisMethodInTryCatchOrIllEatYouException e ) { }
		finally {
			// We don't sourceCard.setPersistentOff();
			// We want the contents to be expanded until we're done with this object
		}

		sequence = sourceCard.getProperty(PropertyName.SEQUENCE);
		if ( sequence == null )
			sequence = new AcalProperty("SEQUENCE", "1");
		uid = sourceCard.getProperty(PropertyName.UID);
		if ( uid == null ) {
			uid = new AcalProperty("UID", vCardRow.getAsString(DavResources._ID));
		}
		buildTypeMap();
		
	}

	
	/**
	 * Traverses the properties, building an index by type and another by association.
	 * 
	 * VCARD properties may be either like "PROPERTY:VALUE" or possibly as "aname.property:VALUE" (case is irrelevant) and
	 * this is building an index so we can get all "PROPERTY" properties from typeMap and all "aname" properties from groupMap
	 * 
	 */
	private void buildTypeMap() {
		typeMap = new HashMap<String,Set<AcalProperty>>();
		groupMap = new HashMap<String,Set<AcalProperty>>();

		AcalProperty[] vCardProperties = sourceCard.getAllProperties();
		String[] nameSplit;
		Set<AcalProperty> s;
		for( AcalProperty prop : vCardProperties ) {
			nameSplit = simpleSplit.split(prop.getName().toUpperCase(Locale.US),2);
			if ( nameSplit.length == 1 ) {
				s = typeMap.get(nameSplit[0]);
				if ( s == null ) {
					s = new HashSet<AcalProperty>();
					typeMap.put(nameSplit[0], s);
				}
				s.add(prop);
			}
			else {
				s = typeMap.get(nameSplit[1]);
				if ( s == null ) {
					s = new HashSet<AcalProperty>();
					typeMap.put(nameSplit[1], s);
				}
				s.add(prop);

				s = groupMap.get(nameSplit[0]);
				if ( s == null ) {
					s = new HashSet<AcalProperty>();
					groupMap.put(nameSplit[0], s);
				}
				s.add(prop);
			}
		}
	}

	public String getUid() {
		return uid.getValue();
	}

	public String getFullName() {
		if ( sourceCard == null ) return null;
		AcalProperty fnProp = sourceCard.getProperty(PropertyName.FN);
		if ( fnProp == null ) return null;
		return fnProp.getValue();
	}

	public int getSequence() {
		Integer result =  Integer.parseInt(sequence.getValue());
		if ( result == null ) result = 1;
		return result;
	}

	
	public void writeToContact(Context context, Account account, Integer androidContactId) {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		if ( androidContactId < 0 ) {
			Log.println(Constants.LOGD,TAG,"Inserting data for '"+sourceCard.getProperty(PropertyName.FN).getValue()+"'");
			ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
						.withValue(RawContacts.ACCOUNT_TYPE, account.type)
						.withValue(RawContacts.ACCOUNT_NAME, account.name)
						.withValue(RawContacts.SYNC1, this.getUid())
						.build());

			this.writeContactDetails(ops, true, 0);
		}
		else {
			Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, androidContactId);
			Log.println(Constants.LOGD,TAG,"Updating data for '"+sourceCard.getProperty(PropertyName.FN).getValue()+"'");
			ops.add(ContentProviderOperation.newUpdate(rawContactUri)
						.withValue(RawContacts.ACCOUNT_TYPE, account.type)
						.withValue(RawContacts.ACCOUNT_NAME, account.name)
						.withValue(RawContacts.SYNC1, this.getUid())
						.withValue(RawContacts.VERSION,this.getSequence())
						.build());

			this.writeContactDetails(ops, false, androidContactId);
		}
		
		try {
			Log.println(Constants.LOGD,TAG,"Applying update batch: "+ops.toString());
			context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
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

	
	private void writeContactDetails(ArrayList<ContentProviderOperation> ops, boolean isInsert, int rawContactId) {
		String propertyName;
		AcalProperty[] vCardProperties = sourceCard.getAllProperties();
		for (AcalProperty prop : vCardProperties) {
			Builder op;
			if ( isInsert ) {
				op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
				op.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
			}
			else {
				op = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, rawContactId));
			}
			propertyName = prop.getName();
			String nameSplit[] = simpleSplit.split(prop.getName().toUpperCase(Locale.US),2);
			propertyName = (nameSplit.length == 2 ? nameSplit[1] : nameSplit[0]);

			if ( propertyName.equals("FN") ) 		doStructuredName(op, prop, sourceCard.getProperty("N"));
			else if ( propertyName.equals("TEL") )	doPhone(op, prop);
			else if ( propertyName.equals("ADR") )	doStructuredAddress(op, prop);
			else if ( propertyName.equals("EMAIL")) doEmail(op, prop);
			else if ( propertyName.equals("PHOTO")) doPhoto(op, prop);
			else
				continue;

			
			Log.println(Constants.LOGD,TAG,"Applying "+propertyName+" change for:"+op.build().toString());
			ops.add(op.build());
		}
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


	private void doPhone(Builder op, AcalProperty telProp ) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		op.withValue(CommonDataKinds.Phone.NUMBER,telProp.getValue());
		String phoneType = telProp.getParam("TYPE");
		if ( phoneType == null )
			phoneType = "OTHER";
		else
			phoneType = phoneType.toUpperCase(); 
		
		if ( phoneType.contains("HOME") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_HOME);
		}
		else if ( phoneType.contains("WORK") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK);
		}
		else if ( phoneType.contains("CELL") ) {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE);
		}
		else {
			op.withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_OTHER);
		}
		Log.v(TAG,"Processing field TEL:"+phoneType+":"+telProp.getValue());
	}


	private void doStructuredAddress(Builder op, AcalProperty adrProp) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
		String addressType = adrProp.getParam("TYPE").toUpperCase();
		
		int opTYpe;
		if ( addressType.contains("HOME") ) opTYpe = CommonDataKinds.StructuredPostal.TYPE_HOME;
		else if ( addressType.contains("WORK") ) opTYpe = CommonDataKinds.StructuredPostal.TYPE_WORK;
		else opTYpe =  CommonDataKinds.StructuredPostal.TYPE_OTHER;
		op.withValue(CommonDataKinds.StructuredPostal.TYPE, opTYpe);
	
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


	private void doEmail(Builder op, AcalProperty emailProp) {
		op.withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
		op.withValue(CommonDataKinds.Email.DATA,emailProp.getValue());
		String emailType = emailProp.getParam("TYPE").toUpperCase(); 
		if ( emailType.contains("HOME") ) {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME);
		}
		else if ( emailType.contains("WORK") ) {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK);
		}
		else {
			op.withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_OTHER);
		}
		Log.v(TAG,"Processing field EMAIL:"+emailType+":"+emailProp.getValue());
		
	}


	private void doPhoto(Builder op, AcalProperty prop) {
		byte[] decodedString = Base64Coder.decode(prop.getValue().replaceAll(" ",""));
		op.withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
		op.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO,decodedString);
		Log.v(TAG,"Processing field PHOTO:"+prop.getValue());
	}


	public static ContentValues getAndroidContact(Context context, Integer rawContactId) {
		Uri contactDataUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
		Cursor cur = context.getContentResolver().query(contactDataUri, null, null, null, null);
		try {
			if ( cur.moveToFirst() ) {
				ContentValues result = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(cur, result);
				cur.close();
				return result;
			}
		}
		catch( Exception e ) {
			Log.w(TAG,"Could not retrieve Android contact",e);
		}
		finally {
			if ( cur != null ) cur.close();
		}
		return null;
	}


	public void writeToVCard(Context context, ContentValues androidContact) {
		sourceCard.setEditable();
		
		Log.println( Constants.LOGD, TAG, "I should write this to a VCard!" );
		for( Map.Entry<String,Object> androidValue : androidContact.valueSet() ) {
			String key = androidValue.getKey();
			Object value = androidValue.getValue();
			Log.println( Constants.LOGD, TAG, key+"="+(value == null ? "null" : value.toString()) );
		}
	}
	
}
