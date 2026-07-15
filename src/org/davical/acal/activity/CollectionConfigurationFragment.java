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

import android.content.ContentValues;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.davical.acal.R;
import org.davical.acal.providers.DavCollections;

import java.util.HashMap;

public class CollectionConfigurationFragment extends PreferenceFragmentCompat
		implements Preference.OnPreferenceChangeListener {

	private static final String TAG = "aCal CollectionConfigurationFragment";

	private EditTextPreference displayName;
	private EditTextPreference maxSyncAge3g;
	private EditTextPreference maxSyncAgeWifi;

	private final HashMap<String, String> defaultSummaries = new HashMap<>();

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		defaultSummaries.put(DavCollections.DISPLAYNAME, getString(R.string.A_name_for_this_collection));
		defaultSummaries.put(DavCollections.ACTIVE_ADDRESSBOOK, getString(R.string.Active_addressbook));
		defaultSummaries.put(DavCollections.ACTIVE_EVENTS, getString(R.string.Active_for_events));
		defaultSummaries.put(DavCollections.ACTIVE_JOURNAL, getString(R.string.Active_for_journal));
		defaultSummaries.put(DavCollections.ACTIVE_TASKS, getString(R.string.Active_for_tasks));
		defaultSummaries.put(DavCollections.COLOUR, getString(R.string.The_colour_associated_with_this_collection));
		defaultSummaries.put(DavCollections.DEFAULT_TIMEZONE, getString(R.string.The_timezone_new_events_default_to));
		defaultSummaries.put(DavCollections.MAX_SYNC_AGE_3G, getString(R.string.The_maximum_age_for_data_while_on_3g));
		defaultSummaries.put(DavCollections.MAX_SYNC_AGE_WIFI, getString(R.string.The_maximum_age_for_data_while_on_wifi));
		defaultSummaries.put(DavCollections.USE_ALARMS, getString(R.string.Use_alarms_from_this_calendar));

		buildPreferenceHierarchy();
	}

	private ContentValues data() {
		return ((CollectionConfiguration) requireActivity()).getCollectionData();
	}

	private void buildPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(requireContext());
		root.setPersistent(false);
		setPreferenceScreen(root);

		ContentValues collectionData = data();

		displayName = new EditTextPreference(requireContext());
		displayName.setDialogTitle(getString(R.string.Name));
		displayName.setText(collectionData.getAsString(DavCollections.DISPLAYNAME));
		addPreference(displayName, getString(R.string.Name), DavCollections.DISPLAYNAME);

		if (collectionData.getAsInteger(DavCollections.HOLDS_ADDRESSBOOK) == 1) {
			CheckBoxPreference activeAddressbook = new CheckBoxPreference(requireContext());
			activeAddressbook.setChecked(collectionData.getAsInteger(DavCollections.ACTIVE_ADDRESSBOOK) == 1);
			activeAddressbook.setTitle(R.string.pActive_addressbook);
			addPreference(activeAddressbook, getString(R.string.Active), DavCollections.ACTIVE_ADDRESSBOOK);
		} else {
			if (collectionData.getAsInteger(DavCollections.HOLDS_EVENTS) != null && collectionData.getAsInteger(DavCollections.HOLDS_EVENTS) == 1) {
				CheckBoxPreference activeEvents = new CheckBoxPreference(requireContext());
				activeEvents.setChecked(collectionData.getAsInteger(DavCollections.ACTIVE_EVENTS) != null && collectionData.getAsInteger(DavCollections.ACTIVE_EVENTS) == 1);
				activeEvents.setTitle(R.string.pActive_for_events);
				addPreference(activeEvents, getString(R.string.pActive_for_events), DavCollections.ACTIVE_EVENTS);
			}

			if (collectionData.getAsInteger(DavCollections.HOLDS_TASKS) != null && collectionData.getAsInteger(DavCollections.HOLDS_TASKS) == 1) {
				CheckBoxPreference activeTasks = new CheckBoxPreference(requireContext());
				activeTasks.setChecked(collectionData.getAsInteger(DavCollections.ACTIVE_TASKS) != null && collectionData.getAsInteger(DavCollections.ACTIVE_TASKS) == 1);
				activeTasks.setTitle(R.string.pActive_for_tasks);
				addPreference(activeTasks, getString(R.string.pActive_for_tasks), DavCollections.ACTIVE_TASKS);
			}

			if (collectionData.getAsInteger(DavCollections.HOLDS_JOURNAL) != null && collectionData.getAsInteger(DavCollections.HOLDS_JOURNAL) == 1) {
				CheckBoxPreference activeJournal = new CheckBoxPreference(requireContext());
				activeJournal.setChecked(collectionData.getAsInteger(DavCollections.ACTIVE_JOURNAL) != null && collectionData.getAsInteger(DavCollections.ACTIVE_JOURNAL) == 1);
				activeJournal.setTitle(R.string.pActive_for_journal);
				addPreference(activeJournal, getString(R.string.pActive_for_journal), DavCollections.ACTIVE_JOURNAL);
			}

			CheckBoxPreference activeAlarms = new CheckBoxPreference(requireContext());
			activeAlarms.setChecked(collectionData.getAsInteger(DavCollections.USE_ALARMS) != null && collectionData.getAsInteger(DavCollections.USE_ALARMS) == 1);
			activeAlarms.setTitle(R.string.pUse_Alarms);
			addPreference(activeAlarms, getString(R.string.Use_Alarms), DavCollections.USE_ALARMS);
		}

		ColourPickerPreference collectionColor = new ColourPickerPreference(requireContext(), null);
		try {
			collectionColor.setColor(Color.parseColor(collectionData.getAsString(DavCollections.COLOUR)));
		} catch (Exception e) {
			collectionColor.setColor(0x00FF00);
		}
		addPreference(collectionColor, getString(R.string.Colour), DavCollections.COLOUR);

		maxSyncAge3g = new EditTextPreference(requireContext());
		maxSyncAge3g.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_CLASS_NUMBER));
		maxSyncAge3g.setDialogTitle(getString(R.string.pMax_age_on_3g));
		int currentSyncAge = collectionData.getAsInteger(DavCollections.MAX_SYNC_AGE_3G) != null
				? collectionData.getAsInteger(DavCollections.MAX_SYNC_AGE_3G) : 3600000;
		maxSyncAge3g.setDefaultValue(Integer.toString(currentSyncAge / 60000));
		maxSyncAge3g.setText(Integer.toString(currentSyncAge / 60000));
		addPreference(maxSyncAge3g, getString(R.string.pMax_age_on_3g), DavCollections.MAX_SYNC_AGE_3G);

		maxSyncAgeWifi = new EditTextPreference(requireContext());
		maxSyncAgeWifi.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_CLASS_NUMBER));
		maxSyncAgeWifi.setDialogTitle(getString(R.string.pMax_age_on_wifi));
		int currentWifiAge = collectionData.getAsInteger(DavCollections.MAX_SYNC_AGE_WIFI) != null
				? collectionData.getAsInteger(DavCollections.MAX_SYNC_AGE_WIFI) : 300000;
		maxSyncAgeWifi.setDefaultValue(Integer.toString(currentWifiAge / 60000));
		maxSyncAgeWifi.setText(Integer.toString(currentWifiAge / 60000));
		addPreference(maxSyncAgeWifi, getString(R.string.pMax_age_on_wifi), DavCollections.MAX_SYNC_AGE_WIFI);

		if (collectionData.getAsInteger(DavCollections._ID) != null) {
			addSyncActionPreference(getString(R.string.Sync_collection_now),
					getString(R.string.Sync_collection_now_summary), false);
			addSyncActionPreference(getString(R.string.Force_full_resync),
					getString(R.string.Force_full_resync_summary), true);
		}

		updateTextSummaries();
	}

	private void addSyncActionPreference(String title, String summary, final boolean fullResync) {
		Preference pref = new Preference(requireContext());
		pref.setPersistent(false);
		pref.setTitle(title);
		pref.setSummary(summary);
		pref.setOnPreferenceClickListener(p -> {
			((CollectionConfiguration) requireActivity()).requestSync(fullResync);
			return true;
		});
		getPreferenceScreen().addPreference(pref);
	}

	private void addPreference(Preference pref, String title, String key) {
		pref.setPersistent(false);
		pref.setTitle(title);
		pref.setKey(key);
		pref.setSummary(defaultSummaries.get(key));
		pref.setOnPreferenceChangeListener(this);
		try {
			getPreferenceScreen().addPreference(pref);
		} catch (Exception e) {
			Log.e(TAG, "Failed to add preference " + title + "/" + key, e);
		}
	}

	private void updateTextSummary(EditTextPreference p) {
		if (p == null) return;
		String key = p.getKey();
		String curVal = data().getAsString(key);
		if (curVal == null || curVal.isEmpty()) p.setSummary(defaultSummaries.get(key));
		else p.setSummary(p.getText());
	}

	private void updateTextSummaries() {
		updateTextSummary(displayName);
	}

	@Override
	public boolean onPreferenceChange(@NonNull Preference pref, Object newValue) {
		String key = pref.getKey();
		boolean ret = false;
		if (key.equals(DavCollections.DISPLAYNAME)) ret = validateDisplayName(newValue);
		else if (key.equals(DavCollections.ACTIVE_ADDRESSBOOK)) ret = validateActive(newValue, DavCollections.ACTIVE_ADDRESSBOOK, DavCollections.HOLDS_ADDRESSBOOK);
		else if (key.equals(DavCollections.ACTIVE_EVENTS)) ret = validateActive(newValue, DavCollections.ACTIVE_EVENTS, DavCollections.HOLDS_EVENTS);
		else if (key.equals(DavCollections.ACTIVE_JOURNAL)) ret = validateActive(newValue, DavCollections.ACTIVE_JOURNAL, DavCollections.HOLDS_JOURNAL);
		else if (key.equals(DavCollections.ACTIVE_TASKS)) ret = validateActive(newValue, DavCollections.ACTIVE_TASKS, DavCollections.HOLDS_TASKS);
		else if (key.equals(DavCollections.USE_ALARMS)) ret = validateAlarms(newValue);
		else if (key.equals(DavCollections.COLOUR)) ret = validateColor(newValue);
		else if (key.equals(DavCollections.MAX_SYNC_AGE_3G)) ret = validateSyncAge(newValue, DavCollections.MAX_SYNC_AGE_3G);
		else if (key.equals(DavCollections.MAX_SYNC_AGE_WIFI)) ret = validateSyncAge(newValue, DavCollections.MAX_SYNC_AGE_WIFI);

		((CollectionConfiguration) requireActivity()).onCollectionDataChanged();
		updateTextSummaries();
		return ret;
	}

	private boolean validateDisplayName(Object v) {
		if (v instanceof String && !v.equals("")) {
			data().put(DavCollections.DISPLAYNAME, (String) v);
			return true;
		}
		return false;
	}

	private boolean validateAlarms(Object v) {
		data().put(DavCollections.USE_ALARMS, (Boolean) v ? 1 : 0);
		return true;
	}

	private boolean validateActive(Object v, String updateToField, String maskField) {
		boolean value = (Boolean) v;
		boolean maskValue = (1 == data().getAsInteger(maskField));
		data().put(updateToField, value && maskValue ? 1 : 0);
		return true;
	}

	private boolean validateColor(Object v) {
		data().put(DavCollections.COLOUR, String.format("#%06x", (Integer) v));
		data().put(DavCollections.SYNC_METADATA, 1);
		return true;
	}

	private boolean validateSyncAge(Object v, String field) {
		String value = (String) v;
		if (value.equals("")) {
			data().put(field, "1800000");
			return true;
		}
		long syncAge;
		try {
			syncAge = Long.parseLong(value) * 60000L;
		} catch (Exception e) {
			return false;
		}
		if (syncAge <= 0 || syncAge > Integer.MAX_VALUE) syncAge = Integer.MAX_VALUE;
		data().put(field, syncAge);
		return true;
	}
}
