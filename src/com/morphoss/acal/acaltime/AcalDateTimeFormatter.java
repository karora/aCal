package com.morphoss.acal.acaltime;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.morphoss.acal.StaticHelpers;

public class AcalDateTimeFormatter {

	public static DateFormat longDate = DateFormat.getDateInstance(DateFormat.LONG);
	public static DateFormat shortDate = DateFormat.getDateInstance(DateFormat.SHORT);
	public static DateFormat timeAmPm = new SimpleDateFormat("hh:mmaa");
	public static DateFormat time24Hr = new SimpleDateFormat("HH:mm");
	
	public static DateFormat longDateTime = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
	
	public static String fmtFull( AcalDateTime dateTime, boolean prefer24hourFormat) {
		if ( dateTime == null ) return "- - - - - -";

		Date javaDate = dateTime.toJavaDate();
		StringBuilder b = new StringBuilder();
		if ( !dateTime.isDate() ) {
			b.append((prefer24hourFormat?time24Hr:timeAmPm).format(javaDate).toLowerCase());
			b.append(", ");
		}
		b.append(StaticHelpers.capitaliseWords(longDate.format(javaDate)));
		if ( !dateTime.isDate() && !dateTime.isFloating() ) {
			if ( ! TimeZone.getDefault().getID().equalsIgnoreCase( dateTime.getTimeZoneId() ) ) {
				b.append('\n');
				b.append(dateTime.getTimeZoneId());
			}
		}
		return b.toString();
	}
	
/**
 * TODO - Localisation doesn't work properly See http://code.google.com/p/android/issues/detail?id=12679
 * 
 * 
 * Format an AcalDateTime into a short localised date/time of the format DATE, TIME where Date is a Short date
 * specified by devices localisation settings and time is  hh:mmaa or HH:mm depending on the prefer24hourformat parameter.
 * 
 * If showDateIfToday is false AND if the dateTime passed is todays date, DATE = stringIfToday.
 *  
 * @param dateTime
 * @param prefer24hourFormat
 * @param showDateIfToday
 * @param stringIfToday
 * @return
 */
	public static String fmtShort(AcalDateTime dateTime, boolean prefer24hourFormat, boolean showDateIfToday, String stringIfToday) {
		if ( dateTime == null ) return "- - - -";
		Date javaDate = dateTime.toJavaDate();
		boolean showDate = true;
		if (!showDateIfToday) {
			AcalDateTime now = new AcalDateTime().applyLocalTimeZone();
			if (now.applyLocalTimeZone().getEpochDay() == dateTime.clone().applyLocalTimeZone().getEpochDay()) showDate = false;
		}
		
		
		StringBuilder b = new StringBuilder();
		if (showDate) b.append(shortDate.format(javaDate));
		else b.append(stringIfToday);
		b.append(", ");
		if ( !dateTime.isDate() ) {
			b.append((prefer24hourFormat?time24Hr:timeAmPm).format(javaDate).toLowerCase());
		
		}
		
		if ( !dateTime.isDate() && !dateTime.isFloating() ) {
			if ( ! TimeZone.getDefault().getID().equalsIgnoreCase( dateTime.getTimeZoneId() ) ) {
				b.append(dateTime.getTimeZoneId());
			}
		}
		return b.toString();
	}
}
