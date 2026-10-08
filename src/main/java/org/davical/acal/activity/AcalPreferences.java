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

package org.davical.acal.activity;

import android.os.Bundle;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.davical.acal.R;

public class AcalPreferences extends AcalAppCompatActivity
		implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

	public static final String TAG = "AcalPreferences";

	private CharSequence rootTitle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preferences);
		rootTitle = getString(R.string.appActivitySettings);
		setupToolbarAndDrawer(rootTitle.toString());

		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.preferences_container, new AcalPreferencesFragment())
					.commit();
		}

		getSupportFragmentManager().addOnBackStackChangedListener(this::syncToolbarForBackStack);
	}

	@Override
	public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
		AcalPreferencesFragment fragment = new AcalPreferencesFragment();
		Bundle args = new Bundle();
		args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
		fragment.setArguments(args);
		getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.preferences_container, fragment)
				.addToBackStack(pref.getKey())
				.commit();
		if (getSupportActionBar() != null && pref.getTitle() != null) {
			getSupportActionBar().setTitle(pref.getTitle());
		}
		if (drawerToggle != null) {
			drawerToggle.setDrawerIndicatorEnabled(false);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}
			toolbar.setNavigationOnClickListener(v -> getSupportFragmentManager().popBackStack());
		}
		return true;
	}

	private void syncToolbarForBackStack() {
		FragmentManager fm = getSupportFragmentManager();
		if (fm.getBackStackEntryCount() == 0) {
			if (getSupportActionBar() != null) {
				getSupportActionBar().setTitle(rootTitle);
			}
			if (drawerToggle != null) {
				drawerToggle.setDrawerIndicatorEnabled(true);
				drawerToggle.syncState();
			}
		}
	}

	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
			getSupportFragmentManager().popBackStack();
			return;
		}
		super.onBackPressed();
	}
}
