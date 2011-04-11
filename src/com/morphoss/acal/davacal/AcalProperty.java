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

package com.morphoss.acal.davacal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import com.morphoss.acal.Constants;

/**
 * <p>
 * Holds an iCalendar property with parameters and value
 * </p>
 * 
 * @author Morphoss Ltd
 *
 */
public class AcalProperty {
	public static final String TAG = "aCal AcalProperty";
	private static final Pattern propertyValueSplit = Pattern.compile("^(.*?)(?<!\\\\):(.*)",Pattern.DOTALL);
	private static final Pattern propertyParamSplit = Pattern.compile("(?<!\\\\);");
	private static final Pattern valueReplaceEscaped = Pattern.compile("\\\\([,;'\"\\\\])");
	private static final Pattern propertiesUnescaped = Pattern.compile(
				"^(ATTACH|GEO|PERCENT-COMPLETE|PRIORITY|DURATION|FREEBUSY|TZOFFSETFROM|TZOFFSETTO|TZURL" +
				"|ATTENDEE|ORGANIZER|RECURRENCE-ID|URL|EXRULE|SEQUENCE|CREATED|RRULE|REPEAT" +
				"|TRIGGER|RDATECOMPLETED|DTEND|DUE|DTSTART|DTSTAMP|LAST-MODIFIED|CREATED|EXDATE)$"
				);
	private Map<String, String> params;
	private boolean paramsSet = false;
	private String name;
	private String value;
	String paramsBlob[];

	/**
	 * Construct an AcalProperty object from a string, presumably culled from a VCOMPONENT
	 * of some kind, such as a VEVENT or VCARD.
	 * @param blob
	 * @return
	 */
	public static AcalProperty fromString(String blob) {
		Matcher m = propertyValueSplit.matcher(blob);
		String tmpblob;
		String value;
		String[] paramsBlob;
		
		if (m.matches()) {
			value = blob.substring(m.start(2), m.end(2));
			Matcher m2 = valueReplaceEscaped.matcher(value);
			value = m2.replaceAll("$1");
			value = value.replace("\\n", "\n");
			value = value.replace("\\N", "\n");
			tmpblob = blob.substring(m.start(1), m.end(1));
		} else {
			value = "";
			tmpblob = blob;
		}
		paramsBlob = propertyParamSplit.split(tmpblob);
		String name = paramsBlob[0].toUpperCase();
		
		if (name.equals("RECURRENCE-ID")) return new RecurrenceId(name,value,paramsBlob);
		return new AcalProperty(name,value,paramsBlob);
	}

	/**
	 * Construct an AcalProperty from a name, value and array of parameters.  The parameters 
	 * are unparsed strings.
	 * @param name
	 * @param value
	 * @param paramsBlob
	 */
	protected AcalProperty(String name, String value, String[] paramsBlob) {
		this.name = name;
		this.value = value;
		if ( paramsBlob[0].equals(name) ) {
			this.paramsBlob = paramsBlob;
		}
		else {
			String[] fixedParams = new String[paramsBlob.length + 1];
			fixedParams[0] = name;
			for( int i=0; i<paramsBlob.length; i++) fixedParams[i+1] = paramsBlob[i];
			this.paramsBlob = fixedParams;
		}
	}

	/**
	 * Construct an AcalProperty from a simple name / value pair.
	 * @param name
	 * @param value
	 */
	public AcalProperty(String name, String value) {
		this.name = name;
		this.value = value;
		this.paramsBlob = null;
		this.params = new HashMap<String,String>();
	}

	/**
	 * Set the value of parameter "name" to "value" for this AcalProperty.
	 * @param name
	 * @param value
	 */
	public synchronized void setParam(String name, String value) {
		if (!paramsSet) populateParams();
		paramsBlob = null;
		params.put(name, value);
	}

	private synchronized void rebuildParamsBlob() {
		if ( params == null ) {
			paramsBlob = new String[] { name };
			return;
		}

		paramsBlob = new String[params.size() + 1];
		paramsBlob[0] = name;
		int i=1;
		for( String p : params.keySet() ) {
			StringBuilder builder = new StringBuilder(p.toUpperCase());
			builder.append('=');
			// Should really use pre-compiled regex here, but it should not be called often
			builder.append(params.get(p).replaceAll("([:;,\\\\])", "\\\\$1").replaceAll("\n", "\\\\N"));
			paramsBlob[i++] = builder.toString();
		}
	}
	
	private synchronized void populateParams() {
		if (paramsSet) return;
		if ( params == null ) params = new HashMap<String,String>();
		if ( paramsBlob != null ) {
			for (int i = 1; i< paramsBlob.length; i++) {
				String[] param = paramsBlob[i].split("=");
				if (param.length != 2) {
					if (Constants.LOG_DEBUG) Log.d(TAG, "Error processing property: "+paramsBlob[i]);
					
				}
				else {
					params.put(param[0].toUpperCase(), param[1]);
				}
			}
		}
		this.paramsSet = true;
	}
	
	private synchronized void destroyParams() {
		if (!paramsSet || paramsBlob == null ) return;
		this.params = null;
		this.paramsSet = false;

	}

	/**
	 * Returns the name of this property.
	 * @return
	 */
	public synchronized String getName() {
		return this.name;
	}

	/**
	 * Returns the value of this property.
	 * @return
	 */
	public synchronized  String getValue() {
		return this.value;
	}

	/**
	 * Returns the value of any parameter of the specified name.
	 * @param name
	 * @return
	 */
	public synchronized String getParam(String name) {
		populateParams();
		String ret = params.get(name.toUpperCase());
		destroyParams();
		return ret;
	}

	/**
	 * Retrieves a map of all parameter names and their values.
	 * @return
	 */
	public synchronized Map<String,String> getParams() {
		populateParams();
		Map<String,String> ret = Collections.unmodifiableMap(this.params);
		destroyParams();
		return ret;
	}

	/**
	 * Returns a set of the parameter names.
	 * @return
	 */
	public synchronized Set<String> getParamNames() {
		populateParams();
		Set<String> ret = Collections.unmodifiableSet(params.keySet());
		destroyParams();
		return ret;
	}


	/**
	 * Return the property as an RFC formatted string, including line wrapping. 
	 */
	public synchronized String toString() {
		return toRfcString();
	}

	/**
	 * <p>
	 * Return the property as a String, hopefully replicating it's original format, apart from some
	 * possible differences in the line wrapping algorithm used.  The string will be wrapped to ensure
	 * all lines are length < 72 characters or less. 
	 * </p>
	 */
	public synchronized String toRfcString() {
		StringBuilder paramBuilder = new StringBuilder(name);
		if ( paramsBlob == null ) rebuildParamsBlob();
		for (int i = 1; i< paramsBlob.length; i++) {
			paramBuilder.append(';');
			paramBuilder.append(paramsBlob[i]);
		}
		Matcher m = propertiesUnescaped.matcher(name);
		String escaped_value = (m.matches() ? value : value.replaceAll("([,\\\\])", "\\\\$1").replaceAll("\n", "\\\\N"));

		System.setProperty("file.encoding", "UTF-8");
		int paramLength = paramBuilder.toString().getBytes().length;
		int valueLength = escaped_value.getBytes().length;

		paramBuilder.append(':');
		if ( paramLength + valueLength >= 72 ) {
		    if ( paramLength < 72 && valueLength < 72 ) {
		    	paramBuilder.append(Constants.CRLF);
		    	paramBuilder.append(" ");
		    }
		    else {
		    	return rfc5545Wrap(paramBuilder.append(escaped_value).toString(), 72);
		    }
		}

    	return paramBuilder.append(escaped_value).toString();
	}

	/**
	 * <p>
	 * Wrap the inString to have lines at the indicated maxOctets octet length.  This is more
	 * complex than one might expect, since we're dealing with multi-byte character sets we
	 * cannot wrap inside a character or we will break it.
	 * </p>
	 * @param inString
	 * @param maxOctets
	 * @return
	 */
	private synchronized String rfc5545Wrap(String inString, int maxOctets) {
		StringBuilder outString = new StringBuilder();
		int cutPos;
		while( inString.getBytes().length >= maxOctets ) {
			cutPos = maxOctets;
			while( inString.substring(0, cutPos).getBytes().length >= maxOctets ) cutPos--;
			if ( outString.length() == 0 )
				cutPos--; // Allow for the space after we've done the first line
			else
				outString.append("\r\n ");
			outString.append(inString.substring(0,cutPos));
			inString = inString.substring(cutPos);
		}
		if ( ! inString.equals("") ) outString.append(inString);
		return outString.toString();
	}
}
