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

package com.morphoss.acal;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

import com.morphoss.acal.activity.AcalActivity;
import com.morphoss.acal.activity.JournalListView;
import com.morphoss.acal.service.aCalService;

public class aCalNotes extends AcalActivity {

	final public static String TAG = "aCalNotes";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Check for required permissions before proceeding
		if (!checkPermissions()) {
			// Permission request in progress, wait for callback
			return;
		}

		// Permissions granted, proceed with initialization
		initializeApp();
	}

	/**
	 * Initialize the app after permissions are granted.
	 */
	private void initializeApp() {
		// make sure aCalService is running
		Intent serviceIntent = new Intent(this, aCalService.class);
		serviceIntent.putExtra("UISTARTED", System.currentTimeMillis());
		this.startService(serviceIntent);

		// Set all default preferences to reasonable values
		PreferenceManager.setDefaultValues(this, R.xml.main_preferences, false);

		int lastRevision = prefs.getInt(PrefNames.lastRevision, 0);
		if ( lastRevision == 0 ) {
			// Default our 24hr pref to the system one.
			prefs.edit().putBoolean(getString(R.string.prefTwelveTwentyfour), DateFormat.is24HourFormat(this)).apply();
		}

		startPreferredView(prefs, this);
		this.finish();
	}

	@Override
	protected void onPermissionsGranted() {
		// Permissions were granted via the dialog, now initialize
		initializeApp();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}


	public static void startPreferredView( SharedPreferences sPrefs, Activity c ) {
		Bundle bundle = new Bundle();
		Intent startIntent = new Intent(c, JournalListView.class);
		startIntent.putExtras(bundle);
		c.startActivity(startIntent);
	}
}
