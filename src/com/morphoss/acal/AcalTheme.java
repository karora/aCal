/*
 * Copyright (C) 2012 Morphoss Ltd
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
package com.morphoss.acal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import com.google.android.material.color.DynamicColors;

public final class AcalTheme {

	private static int themeDefaultColour = (Constants.DEBUG_MODE ? 0xffff3020 /* red */ : 0xfff0a020 /* orange */ ); 
	private static int themeButtonColour = (Constants.DEBUG_MODE ? 0xffff3020 /* red */ : 0xfff0a020 /* orange */ ); 
	private static int themeBackgroundColour = 0xffffffff;
	
	private final static int themeTextDark   = 0xff001060;
	private final static int themeTextLight  = 0xfffff8e8; 

	public static final int BUTTON = 1;
	public static final int BACKGROUND = 2;
	private static final String	TAG	= "AcalTheme";

	public static SharedPreferences prefs;
	
	public static void initializeTheme(Context context) {
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int colorPrimary = resolveThemeColour(context, androidx.appcompat.R.attr.colorPrimary);
		int defaultButtonColour = colorPrimary != 0 ? colorPrimary : themeButtonColour;
		themeButtonColour = prefs.getInt(context.getString(R.string.prefThemeButtonColour), defaultButtonColour);
		Log.i(TAG,"Set theme button colour to "+themeButtonColour);
	}

	private static int resolveThemeColour(Context context, int attr) {
		TypedValue tv = new TypedValue();
		if (context.getTheme().resolveAttribute(attr, tv, true)) {
			return tv.data;
		}
		return 0;
	}

	/**
	 * Apply the user-selected colour theme to the given activity. Must be
	 * called before super.onCreate (before setContentView) to take effect.
	 */
	public static void applySelectedTheme(Activity activity) {
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
		String choice = p.getString(PrefNames.appTheme, "pink");
		if ("dynamic".equals(choice) && DynamicColors.isDynamicColorAvailable()) {
			activity.setTheme(themeResForKey("pink"));
			DynamicColors.applyToActivityIfAvailable(activity);
			return;
		}
		activity.setTheme(themeResForKey(choice));
	}

	private static int themeResForKey(String key) {
		if (key == null) return R.style.Theme_aCal_Pink;
		switch (key) {
			case "teal":   return R.style.Theme_aCal_Teal;
			case "indigo": return R.style.Theme_aCal_Indigo;
			case "green":  return R.style.Theme_aCal_Green;
			case "amber":  return R.style.Theme_aCal_Amber;
			case "pink":
			default:       return R.style.Theme_aCal_Pink;
		}
	}

	final public static View getContainerView(View someView) {
		ViewParent vp;
		do {
			vp = someView.getParent();
		} while ( !(vp instanceof View) );
		return (View) vp;
	}

	/**
	 * Get the colour used for these theme elements
	 *
	 * @param themeElementID
	 */
	final public static int getElementColour(int themeElementID) {
		switch(themeElementID) {
			case BUTTON:		return themeButtonColour;
			case BACKGROUND:	return themeBackgroundColour;
		}
		return themeDefaultColour;
	}

	/**
	 * Get the toolbar colour - the button colour darkened by 30%
	 */
	public static int getToolbarColour() {
		return darkenColour(themeButtonColour, 0.7f);
	}

	/**
	 * Darken a colour by the given factor (0.0 = black, 1.0 = unchanged)
	 */
	public static int darkenColour(int colour, float factor) {
		int a = (colour >> 24) & 0xff;
		int r = (int) (((colour >> 16) & 0xff) * factor);
		int g = (int) (((colour >> 8) & 0xff) * factor);
		int b = (int) ((colour & 0xff) * factor);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	
	
	/**
	 * The way we are theming some things is to have a LinearLayout with a solid
	 * background assigned, containing (normally) a button with a 9-patch which is
	 * white/transparent overlay.  This utility helps us set the colour on the
	 * relevant object.
	 * 
	 * @param someView
	 * @param themeElementID
	 */
	public static void setContainerFromTheme(View someView, int themeElementID) {
		if ( someView instanceof TextView ) {
			try {
				((TextView) someView).setTextColor(pickForegroundForBackground(getElementColour(themeElementID)));
			}
			catch( Exception e ) {};
		}
		setContainerColour(someView, getElementColour(themeElementID));
	}

	
	/**
	 * The way we are theming some things is to have a LinearLayout with a solid
	 * background assigned, containing (normally) a button with a 9-patch which is
	 * white/transparent overlay.  This utility helps us set an explicit (unthemed)
	 * colour on the relevant object.
	 * 
	 * @param someView
	 * @param colour
	 */
	public static void setContainerColour(View someView, int colour) {
		getContainerView(someView).setBackgroundColor(colour);
	}

	
	/**
	 * Given the supplied background colour, tries to pick a foreground colour with good
	 * contrast which is not too jarring against it.
	 * 
	 * This initial implementation is simplistic and probably needs refinement.
	 * 
	 * @param backgroundColour
	 * @return
	 */
	public static int pickForegroundForBackground( int backgroundColour ) {
		int r = (backgroundColour >> 16) & 0xff;
		int g = (backgroundColour >> 8) & 0xff;
		int b = (backgroundColour & 0xff);

		if (( r > 200 && g < 50 && b < 50 ) || ( b > 200 && g < 50 && r < 50 ) || ( g > 200 && r < 50 && b < 50 ) ) {
			if ( Constants.debugTheming ) Log.println(Constants.LOGD, TAG,
					"Choosing ("+(255 - g)+","+(255 - b)+","+(255 - r)+") foreground for ("+r+","+g+","+b+")");
			return 0xff000000 | ((255 - g) << 16) | ((255 - b) << 8) | (255 - r);
		}

		if ( ((r + g + b) / 3) < 120 ) {
			if ( Constants.debugTheming ) Log.println(Constants.LOGD, TAG,
					"Choosing a white foreground for ("+r+","+g+","+b+")");
			return themeTextLight;
		}
		if ( Constants.debugTheming ) Log.println(Constants.LOGD, TAG,
				"Choosing a black foreground for ("+r+","+g+","+b+")");
		return themeTextDark;
	}
	
}
