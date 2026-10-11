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

package org.davical.acal.davacal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import android.util.Log;

import org.davical.acal.Constants;

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
	private static boolean DEBUG = false;
	private static final Pattern propertiesUnescaped = Pattern.compile(
				"^(ATTACH|GEO|PERCENT-COMPLETE|PRIORITY|DURATION|FREEBUSY|TZOFFSETFROM|TZOFFSETTO|TZURL" +
				"|ATTENDEE|ORGANIZER|RECURRENCE-ID|URL|EXRULE|SEQUENCE|CREATED|RRULE|REPEAT" +
				"|TRIGGER|RDATE|COMPLETED|DTEND|DUE|DTSTART|DTSTAMP|LAST-MODIFIED|CREATED|EXDATE)$"
				);
	private Map<String, String> params;
	private boolean paramsPersistent = false;
	private boolean paramsSet = false;
	public final String name;
	private final String value;
	/**
	 * The value exactly as it was read, for a property which was.  Reading takes the
	 * backslashes out, and what they said cannot be worked out again from the name of the
	 * property: "A,B" in CATEGORIES is two categories and "A\,B" is one.  So what is written
	 * back is what was read.
	 */
	private final String rawValue;
	private String paramsBlob[];

	// These parameters are quite common so we'll use string parameters
	public final static String PARAM_VALUE = "VALUE";
	public final static String PARAM_TYPE = "TYPE";
	public final static String PARAM_TZID = "TZID";

	/**
	 * Construct an AcalProperty object from a string, presumably culled from a VCOMPONENT
	 * of some kind, such as a VEVENT or VCARD.
	 * @param blob
	 * @return
	 */
	public static AcalProperty fromString(String blob) {
		if ( blob == null ) throw new NullPointerException();
		String tmpblob;
		String value;
		String rawValue;
		String[] paramsBlob;

		int splitPos = findNextUnescaped(':',0,blob);
		if ( splitPos < blob.length() ) {
			rawValue = blob.substring(splitPos + 1);
			value = unescape(rawValue);
			tmpblob = blob.substring(0,splitPos);
		}
		else {
			rawValue = "";
			value = "";
			tmpblob = blob;
		}

		String name = null;
		ArrayList<String> params = new ArrayList<String>();
		splitPos = 0;
		String pblob = tmpblob;
		while( pblob != null ) {
			splitPos = findNextUnescaped(';',++splitPos,pblob);

			if ( splitPos < pblob.length() ) {
				tmpblob = pblob.substring(0,splitPos);
				pblob = ((splitPos+1) == pblob.length() ? null : pblob.substring(splitPos + 1));
			}
			else {
				tmpblob = pblob;
				pblob = null;
			}
			if ( name == null ) {
				name = tmpblob.toUpperCase(Locale.ENGLISH);
			}
			else {
				params.add(tmpblob);
			}

		}

		paramsBlob = ( params.size() > 0 ? params.toArray(new String[params.size()]) : new String[0]);

		if (name.equals("RECURRENCE-ID")) return new RecurrenceId(value,paramsBlob);
		return new AcalProperty(name,value,paramsBlob,rawValue);
	}

	/**
	 * Takes the backslashes out of a value as it was read: "\n" or "\N" is a new line, and
	 * a backslash before a comma, semicolon, quote or backslash gives that character.  Any
	 * other backslash is left where it is.
	 */
	static String unescape(String raw) {
		if ( raw.indexOf('\\') < 0 ) return raw;
		StringBuilder plain = new StringBuilder(raw.length());
		for( int i=0; i < raw.length(); i++ ) {
			char ch = raw.charAt(i);
			if ( ch == '\\' && i + 1 < raw.length() ) {
				char next = raw.charAt(i + 1);
				if ( next == 'n' || next == 'N' ) {
					plain.append('\n');
					i++;
					continue;
				}
				if ( next == ',' || next == ';' || next == '\'' || next == '"' || next == '\\' ) {
					plain.append(next);
					i++;
					continue;
				}
			}
			plain.append(ch);
		}
		return plain.toString();
	}

	/**
	 * Puts the backslashes that RFC 5545 asks for into a TEXT value: before a backslash, a
	 * comma or a semicolon, and "\n" for a new line.
	 */
	static String escapeText(String plain) {
		StringBuilder escaped = new StringBuilder(plain.length() + 8);
		for( int i=0; i < plain.length(); i++ ) {
			char ch = plain.charAt(i);
			if ( ch == '\n' ) escaped.append("\\n");
			else {
				if ( ch == '\\' || ch == ',' || ch == ';' ) escaped.append('\\');
				escaped.append(ch);
			}
		}
		return escaped.toString();
	}

	/**
	 * Find the next unescaped 'c', start looking at 'startFrom' within 'blob'
	 * @param c
	 * @param startFrom
	 * @param blob
	 * @return The position we expect to find it at.
	 */
	private static int findNextUnescaped(char c, int startFrom, String blob) {
		char ch;
		int pos=startFrom;
		boolean inQuotes = false;
		while( pos < blob.length() ) {
			ch = blob.charAt(pos);
			if ( !inQuotes && ch == c )
				break;
			else if ( ch == '"' )
				inQuotes = !inQuotes;
			else if ( ch == '\\' )
				pos++;
			pos++;
		}
		return pos;
	}

	/**
	 * Construct an AcalProperty from a name, value and array of parameters.  The parameters
	 * are unparsed strings.
	 * @param name
	 * @param value
	 * @param paramsBlob
	 */
	protected AcalProperty(String name, String value, String[] paramsBlob) {
		this(name, value, paramsBlob, null);
	}

	private AcalProperty(String name, String value, String[] paramsBlob, String rawValue) {
		this.name = name;
		this.value = value;
		this.rawValue = rawValue;
		this.paramsBlob = paramsBlob;
	}

	/**
	 * Construct an AcalProperty from a simple name / value pair.
	 * @param name
	 * @param value
	 */
	public AcalProperty(String name, String value) {
		this.name = name;
		this.value = value;
		this.rawValue = null;
		this.paramsBlob = new String[] { };
		this.params = new HashMap<String,String>();
	}

	public AcalProperty(PropertyName knownProperty, String pValue) {
		this(knownProperty.toString(), pValue );
	}

	/**
	 * Set the value of parameter "name" to "value" for this AcalProperty.
	 * @param paramName
	 * @param paramValue
	 */
	public synchronized void setParam(String paramName, String paramValue) {
		paramsPersistent = true;
		if (!paramsSet) populateParams();
		paramsBlob = null;
		params.put(paramName, paramValue);
		if ( DEBUG && Constants.LOG_VERBOSE )
			Log.v(TAG,"Added to '"+name+"' parameter '"+paramName+"' = '"+paramValue+"'");
	}

	/**
	 * Remove any parameter "name" from this AcalProperty.
	 * @param paramName
	 */
	public synchronized void removeParam(String paramName) {
		paramsPersistent = true;
		if (!paramsSet) populateParams();
		paramsBlob = null;
		params.remove(paramName);
		if ( DEBUG && Constants.LOG_VERBOSE )
			Log.v(TAG,"Removed '"+name+"' parameter '"+paramName+"'");
	}

	public synchronized void rebuildParamsBlob() {
		if ( !paramsSet ) populateParams();
		if ( params == null || params.isEmpty() ) {
			paramsBlob = new String[] { };
			if ( DEBUG && Constants.LOG_VERBOSE )
				Log.v(TAG,"Rebuilt empty paramsBlob for '"+name+"'");
			return;
		}

		if ( DEBUG && Constants.LOG_VERBOSE )
			Log.v(TAG,"Rebuilding paramsBlob for '"+name+"'");
		paramsBlob = new String[params.size()];
		int i=0;
		for( String p : params.keySet() ) {
			if ( params.get(p) == null ) continue;
			StringBuilder builder = new StringBuilder(p.toUpperCase());
			builder.append('=');
			// Should really use pre-compiled regex here, but it should not be called often
			builder.append(params.get(p).replaceAll("([:;,\\\\])", "\\\\$1").replaceAll("\n", "\\\\N"));
			paramsBlob[i++] = builder.toString();
			if ( DEBUG && Constants.LOG_VERBOSE )
				Log.v(TAG,"Blob was'"+builder.toString()+"'");
		}
	}

	private synchronized void populateParams() {
		if (paramsSet) return;
		if ( params == null ) params = new HashMap<String,String>();
		if ( paramsBlob != null ) {
			for (int i = 0; i< paramsBlob.length; i++) {
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
		if ( paramsPersistent || !paramsSet ) return;
		this.params = null;
		this.paramsSet = false;

		if ( DEBUG && Constants.LOG_VERBOSE ) {
			Log.v(TAG,"Params destroyed for '"+name+"'");
			Log.d(TAG, Log.getStackTraceString(new Exception()) );
		}
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
	 * @return The parameter value, or null if not found.
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
		if ( DEBUG && Constants.LOG_VERBOSE )
			Log.v(TAG,"Building RFC String for '"+name+"'");

		StringBuilder paramBuilder = new StringBuilder(name);
		if ( paramsBlob == null ) rebuildParamsBlob();
		for (int i = 0; i< paramsBlob.length; i++) {
			paramBuilder.append(';');
			paramBuilder.append(paramsBlob[i]);
		}
		String escaped_value;
		if ( rawValue != null ) escaped_value = rawValue;
		else if ( propertiesUnescaped.matcher(name).matches() ) escaped_value = value;
		else escaped_value = escapeText(value);

		int paramLength = paramBuilder.toString().getBytes(StandardCharsets.UTF_8).length;
		int valueLength = escaped_value.getBytes(StandardCharsets.UTF_8).length;

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

    	paramBuilder.append(escaped_value);
		if ( DEBUG && Constants.LOG_VERBOSE )
			Log.v(TAG, "AsBuilt>>>"+paramBuilder.toString() );
    	return paramBuilder.toString();
	}

	/**
	 * <p>
	 * Fold the inString so that no line of it is longer than maxOctets octets, counting the
	 * space that starts each continuation line.  A line is never folded inside a character:
	 * doing that would leave something that is not valid UTF-8 on each side of the fold.
	 * </p>
	 * @param inString
	 * @param maxOctets
	 * @return
	 */
	static String rfc5545Wrap(String inString, int maxOctets) {
		StringBuilder outString = new StringBuilder(inString.length() + 16);
		int lineOctets = 0;
		int pos = 0;
		while( pos < inString.length() ) {
			int codePoint = inString.codePointAt(pos);
			int octets = ( codePoint < 0x80 ? 1 : ( codePoint < 0x800 ? 2 : ( codePoint < 0x10000 ? 3 : 4 ) ) );
			if ( lineOctets + octets > maxOctets ) {
				outString.append("\r\n ");
				lineOctets = 1;
			}
			outString.appendCodePoint(codePoint);
			lineOctets += octets;
			pos += Character.charCount(codePoint);
		}
		return outString.toString();
	}
}
