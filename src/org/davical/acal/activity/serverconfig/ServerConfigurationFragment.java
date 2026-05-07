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

package org.davical.acal.activity.serverconfig;

import android.content.ContentValues;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.morphoss.acal.R;
import org.davical.acal.providers.Servers;

import java.util.HashMap;

public class ServerConfigurationFragment extends PreferenceFragmentCompat
		implements Preference.OnPreferenceChangeListener {

	private EditTextPreference friendlyName;
	private EditTextPreference calendarUserURL;
	private EditTextPreference hostname;
	private EditTextPreference port;
	private EditTextPreference principalPath;
	private EditTextPreference username;
	private EditTextPreference password;

	private final HashMap<String, String> defaultSummaries = new HashMap<>();

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		defaultSummaries.put(Servers.FRIENDLY_NAME, getString(R.string.A_name_for_this_server_configuration));
		defaultSummaries.put(Servers.SUPPLIED_USER_URL, getString(R.string.A_URL_or_domain_name));
		defaultSummaries.put(Servers.USE_SSL, getString(R.string.Whether_to_use_encryption));
		defaultSummaries.put(Servers.HOSTNAME, getString(R.string.The_servers_hostname));
		defaultSummaries.put(Servers.PORT, getString(R.string.The_port_to_connect_to));
		defaultSummaries.put(Servers.PRINCIPAL_PATH, getString(R.string.The_path_on_the_server));
		defaultSummaries.put(Servers.AUTH_TYPE, getString(R.string.The_authentication_type_used));
		defaultSummaries.put(Servers.USERNAME, getString(R.string.The_username_to_use));
		defaultSummaries.put(Servers.PASSWORD, getString(R.string.The_password_to_use));
		defaultSummaries.put(Servers.ACTIVE, getString(R.string.Whether_this_server_is_active));

		buildPreferenceHierarchy();
		updateTextSummaries();
	}

	private ContentValues data() {
		return ((ServerConfiguration) requireActivity()).getServerData();
	}

	private boolean isAdvanced() {
		return ((ServerConfiguration) requireActivity()).isAdvancedInterface();
	}

	/** Rebuild the tree — called by the activity when simple/advanced toggles. */
	void rebuild() {
		buildPreferenceHierarchy();
		updateTextSummaries();
	}

	private void buildPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(requireContext());
		root.setPersistent(false);
		setPreferenceScreen(root);

		ContentValues serverData = data();

		friendlyName = new EditTextPreference(requireContext());
		friendlyName.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS));
		friendlyName.setDialogTitle(getString(R.string.Name));
		friendlyName.setText(serverData.getAsString(Servers.FRIENDLY_NAME));
		addPreference(friendlyName, getString(R.string.Name), Servers.FRIENDLY_NAME);

		CheckBoxPreference togglePref = new CheckBoxPreference(requireContext());
		togglePref.setChecked(serverData.getAsInteger(Servers.ACTIVE) == 1);
		addPreference(togglePref, getString(R.string.Active), Servers.ACTIVE);

		username = new EditTextPreference(requireContext());
		username.setDialogTitle(getString(R.string.Username));
		username.setDefaultValue(serverData.getAsString(Servers.USERNAME));
		username.setText(serverData.getAsString(Servers.USERNAME));
		addPreference(username, getString(R.string.Username), Servers.USERNAME);

		password = new EditTextPreference(requireContext());
		password.setOnBindEditTextListener(et -> {
			et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			et.setTransformationMethod(new PasswordTransformationMethod());
		});
		password.setDialogTitle(getString(R.string.Password));
		Object pwd = serverData.get(Servers.PASSWORD);
		if (pwd != null) {
			password.setDefaultValue(pwd);
			password.setText(pwd.toString());
		}
		addPreference(password, getString(R.string.Password), Servers.PASSWORD);

		if (!isAdvanced()) {
			calendarUserURL = new EditTextPreference(requireContext());
			calendarUserURL.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_TEXT_VARIATION_URI));
			calendarUserURL.setDialogTitle(getString(R.string.Simple_User_URL));
			calendarUserURL.setDefaultValue(serverData.getAsString(Servers.SUPPLIED_USER_URL));
			calendarUserURL.setText(serverData.getAsString(Servers.SUPPLIED_USER_URL));
			addPreference(calendarUserURL, getString(R.string.Simple_User_URL), Servers.SUPPLIED_USER_URL);
		} else {
			hostname = new EditTextPreference(requireContext());
			hostname.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_TEXT_VARIATION_URI));
			hostname.setDialogTitle(getString(R.string.Server_Name));
			hostname.setDefaultValue(serverData.getAsString(Servers.HOSTNAME));
			hostname.setText(serverData.getAsString(Servers.HOSTNAME));
			addPreference(hostname, getString(R.string.Server_Name), Servers.HOSTNAME);

			principalPath = new EditTextPreference(requireContext());
			principalPath.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_TEXT_VARIATION_URI));
			principalPath.setDialogTitle(getString(R.string.Server_Path));
			principalPath.setDefaultValue(serverData.getAsString(Servers.PRINCIPAL_PATH));
			principalPath.setText(serverData.getAsString(Servers.PRINCIPAL_PATH));
			addPreference(principalPath, getString(R.string.Server_Path), Servers.PRINCIPAL_PATH);

			ListPreference listPref = new ListPreference(requireContext());
			listPref.setEntries(new String[]{"None", "Basic", "Digest"});
			listPref.setEntryValues(new String[]{
					Integer.toString(Servers.AUTH_NONE),
					Integer.toString(Servers.AUTH_BASIC),
					Integer.toString(Servers.AUTH_DIGEST)
			});
			listPref.setDialogTitle(getString(R.string.Authentication_Type));
			listPref.setDefaultValue(serverData.getAsString(Servers.AUTH_TYPE));
			listPref.setValue(serverData.getAsString(Servers.AUTH_TYPE));
			addPreference(listPref, getString(R.string.Authentication_Type), Servers.AUTH_TYPE);

			CheckBoxPreference sslPref = new CheckBoxPreference(requireContext());
			sslPref.setChecked(serverData.getAsInteger(Servers.USE_SSL) == 1);
			addPreference(sslPref, getString(R.string.Use_SSL), Servers.USE_SSL);

			port = new EditTextPreference(requireContext());
			port.setOnBindEditTextListener(et -> et.setInputType(InputType.TYPE_CLASS_NUMBER));
			port.setDialogTitle(getString(R.string.Server_Port));
			port.setDefaultValue(serverData.getAsString(Servers.PORT));
			port.setText(serverData.getAsString(Servers.PORT));
			addPreference(port, getString(R.string.Server_Port), Servers.PORT);
		}
	}

	private void addPreference(Preference pref, String title, String key) {
		pref.setPersistent(false);
		pref.setTitle(title);
		pref.setKey(key);
		pref.setSummary(defaultSummaries.get(key));
		pref.setOnPreferenceChangeListener(this);
		getPreferenceScreen().addPreference(pref);
	}

	private void checkTextSummary(EditTextPreference p) {
		if (p == null) return;
		String key = p.getKey();
		String curVal = data().getAsString(key);
		if (curVal == null || curVal.isEmpty()) p.setSummary(defaultSummaries.get(key));
		else p.setSummary(p.getText());
	}

	private void updateTextSummaries() {
		checkTextSummary(friendlyName);
		checkTextSummary(username);
		checkTextSummary(calendarUserURL);
		if (isAdvanced()) {
			checkTextSummary(hostname);
			checkTextSummary(principalPath);
			checkTextSummary(port);
		}
	}

	@Override
	public boolean onPreferenceChange(@NonNull Preference pref, Object newValue) {
		String key = pref.getKey();
		boolean ret = false;
		if (key.equals(Servers.FRIENDLY_NAME)) ret = validateFriendlyName(newValue);
		else if (key.equals(Servers.USE_SSL)) ret = validateUseSSL(pref, newValue);
		else if (key.equals(Servers.HOSTNAME)) ret = validateHostName(newValue);
		else if (key.equals(Servers.PORT)) ret = validatePort(newValue);
		else if (key.equals(Servers.SUPPLIED_USER_URL)) ret = validateUrl(newValue);
		else if (key.equals(Servers.PRINCIPAL_PATH)) ret = validatePrincipalPath(newValue);
		else if (key.equals(Servers.AUTH_TYPE)) ret = validateAuth(newValue);
		else if (key.equals(Servers.USERNAME)) ret = validateUsername(newValue);
		else if (key.equals(Servers.PASSWORD)) ret = validatePassword(newValue);
		else if (key.equals(Servers.ACTIVE)) ret = validateActive(newValue);

		((ServerConfiguration) requireActivity()).onServerDataChanged();
		updateTextSummaries();
		return ret;
	}

	private boolean validateFriendlyName(Object v) {
		if (v instanceof String && !v.equals("")) {
			data().put(Servers.FRIENDLY_NAME, (String) v);
			return true;
		}
		return false;
	}

	private boolean validateUseSSL(Preference p, Object v) {
		CheckBoxPreference cbp = (CheckBoxPreference) p;
		boolean value = (Boolean) v;
		data().put(Servers.USE_SSL, value ? 1 : 0);
		int curPort = 0;
		if (!data().getAsString(Servers.PORT).equals("")) {
			curPort = data().getAsInteger(Servers.PORT);
		}

		if (value) {
			if (!cbp.isChecked() && curPort == 80) {
				if (port != null) port.setText("443");
				data().put(Servers.PORT, 443);
			}
		} else {
			if (cbp.isChecked() && curPort == 443) {
				if (port != null) port.setText("80");
				data().put(Servers.PORT, 80);
			}
		}
		return true;
	}

	private boolean validateHostName(Object v) {
		if (v instanceof String && !v.equals("")) {
			data().put(Servers.HOSTNAME, (String) v);
		}
		return false;
	}

	private boolean validateUrl(Object v) {
		if (v instanceof String && !v.equals("")) {
			data().put(Servers.SUPPLIED_USER_URL, (String) v);
		}
		return false;
	}

	private boolean validatePort(Object v) {
		String value = (String) v;
		if (value.equals("")) {
			data().put(Servers.PORT, "");
			return true;
		}
		int p;
		try {
			p = Integer.parseInt(value);
		} catch (Exception e) {
			return false;
		}
		if (p < 0) return false;
		data().put(Servers.PORT, p);
		return true;
	}

	private boolean validatePrincipalPath(Object v) {
		if (v == null || v.equals("")) v = "/";
		String s = (String) v;
		if (!s.substring(0, 1).equals("/")) s = "/" + s;
		data().put(Servers.PRINCIPAL_PATH, s);
		return true;
	}

	private boolean validateAuth(Object v) {
		data().put(Servers.AUTH_TYPE, Integer.parseInt((String) v));
		return true;
	}

	private boolean validateUsername(Object v) {
		data().put(Servers.USERNAME, (String) v);
		return true;
	}

	private boolean validatePassword(Object v) {
		data().put(Servers.PASSWORD, (String) v);
		return true;
	}

	private boolean validateActive(Object v) {
		data().put(Servers.ACTIVE, (Boolean) v ? 1 : 0);
		return true;
	}
}
