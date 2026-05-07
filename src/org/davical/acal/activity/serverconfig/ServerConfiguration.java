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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import org.davical.acal.AcalTheme;
import org.davical.acal.Constants;
import org.davical.acal.PrefNames;
import com.morphoss.acal.R;
import org.davical.acal.ServiceManager;
import org.davical.acal.activity.AcalAppCompatActivity;
import org.davical.acal.providers.Servers;
import org.davical.acal.service.ServiceRequest;

/**
 * <p>This activity allows the user to configure a single server. It MUST be handed a ContentValues object with as
 * an extra on the intent using key "ServerData".</p>
 *
 * <p>The ContentValues object MUST have a value with the key
 * ServerConfiguration.MODEKEY. If it is set to MODE_CREATE it must also contain all the fields for a server
 * as defined by the dav_server table schema. Failure to provide required details of the required type
 * may cause unexpected behaviour.</p>
 *
 * <p>This configuration screen uses AndroidX PreferenceFragmentCompat but does not persist data to
 * SharedPreferences — data is persisted manually via the ContentResolver on apply.</p>
 */
public class ServerConfiguration extends AcalAppCompatActivity
		implements OnClickListener, ServerConfigurator {

	public static final String TAG = "ServerConfiguration";

	private ContentValues serverData;
	private ContentValues originalServerData;
	public int iface = INTERFACE_SIMPLE;

	public static final String KEY_MODE = "MODE";
	public static final String KEY_IMAGE = "IMAGE_RESOURCE";

	public static final int MODE_EDIT = 1;
	public static final int MODE_CREATE = 2;
	public static final int MODE_IMPORT = 3;

	public static final int INTERFACE_SIMPLE = 0;
	public static final int INTERFACE_ADVANCED = 1;

	private Button apply;
	private Button cancel;

	private ServiceManager serviceManager;
	private ServerConfigurationFragment fragment;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.server_config);

		toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
			setSupportActionBar(toolbar);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}
			toolbar.setNavigationOnClickListener(v -> finish());
		}

		apply = findViewById(R.id.ServerConfigApplyButton);
		apply.setOnClickListener(this);
		apply.setEnabled(false);
		cancel = findViewById(R.id.ServerConfiCancelButton);
		cancel.setOnClickListener(this);

		try {
			serverData = getIntent().getExtras().getParcelable("ServerData");
		} catch (Exception e) {
			serverData = new ContentValues();
			serverData.put(KEY_MODE, MODE_CREATE);
		}
		if (serverData == null || !serverData.containsKey(KEY_MODE)) {
			finish();
			return;
		}
		if (serverData.getAsInteger(KEY_MODE) == MODE_CREATE) {
			createDefaultValues();
		} else if (serverData.getAsInteger(KEY_MODE) == MODE_IMPORT) {
			createDefaultValuesForMissing();
		} else if (serverData.getAsInteger(KEY_MODE) == MODE_EDIT) {
			if (!	serverData.containsKey(Servers.FRIENDLY_NAME) &&
					serverData.containsKey(Servers.SUPPLIED_USER_URL) &&
					serverData.containsKey(Servers.HOSTNAME) &&
					serverData.containsKey(Servers.PRINCIPAL_PATH) &&
					serverData.containsKey(Servers.USERNAME) &&
					serverData.containsKey(Servers.PASSWORD) &&
					serverData.containsKey(Servers.PORT) &&
					serverData.containsKey(Servers.AUTH_TYPE) &&
					serverData.containsKey(Servers.ACTIVE) &&
					serverData.containsKey(Servers.USE_SSL)) {
				finish();
				return;
			}
		} else {
			finish();
			return;
		}

		originalServerData = new ContentValues();
		originalServerData.putAll(serverData);

		if (getSupportActionBar() != null) {
			String name = serverData.getAsString(Servers.FRIENDLY_NAME);
			getSupportActionBar().setTitle(name != null && !name.isEmpty() ? name : getString(R.string.appActivityServerConfiguration));
		}

		if (bundle == null) {
			fragment = new ServerConfigurationFragment();
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.server_config_container, fragment)
					.commit();
		} else {
			fragment = (ServerConfigurationFragment) getSupportFragmentManager()
					.findFragmentById(R.id.server_config_container);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (serviceManager != null) serviceManager.close();
	}

	@Override
	public void onResume() {
		super.onResume();
		serviceManager = new ServiceManager(this);
	}

	private void createDefaultValuesForMissing() {
		if (!serverData.containsKey(Servers.FRIENDLY_NAME)) serverData.put(Servers.FRIENDLY_NAME, "");
		if (!serverData.containsKey(Servers.HOSTNAME)) serverData.put(Servers.HOSTNAME, "");
		if (!serverData.containsKey(Servers.SUPPLIED_USER_URL)) serverData.put(Servers.SUPPLIED_USER_URL, "");
		if (!serverData.containsKey(Servers.USERNAME)) serverData.put(Servers.USERNAME, "");
		if (!serverData.containsKey(Servers.PASSWORD)) serverData.put(Servers.PASSWORD, "");
		if (!serverData.containsKey(Servers.PORT)) serverData.put(Servers.PORT, "");
		if (!serverData.containsKey(Servers.AUTH_TYPE)) serverData.put(Servers.AUTH_TYPE, 1);
		if (!serverData.containsKey(Servers.ACTIVE)) serverData.put(Servers.ACTIVE, 1);
		if (!serverData.containsKey(Servers.USE_SSL)) serverData.put(Servers.USE_SSL, 1);
		apply.setEnabled(true);
		serverData.put(KEY_MODE, MODE_CREATE);
	}

	private void createDefaultValues() {
		serverData.put(Servers.FRIENDLY_NAME, "");
		serverData.put(Servers.HOSTNAME, "");
		serverData.put(Servers.SUPPLIED_USER_URL, "");
		serverData.put(Servers.USERNAME, "");
		serverData.put(Servers.PASSWORD, "");
		serverData.put(Servers.PORT, "");
		serverData.put(Servers.AUTH_TYPE, 1);
		serverData.put(Servers.ACTIVE, 1);
		serverData.put(Servers.USE_SSL, 1);
	}

	public void onClick(View v) {
		if (v.getId() == apply.getId()) applyButton();
		else if (v.getId() == cancel.getId()) cancelButton();
	}

	private void cancelButton() {
		finish();
	}

	private void applyButton() {
		if (serverData.getAsInteger(Servers.ACTIVE) == 1) {
			checkServer();
		} else {
			saveData();
			finish();
		}
	}

	private void checkServer() {
		Context cx = this.getBaseContext();
		CheckServerDialog csd = new CheckServerDialog(this, serverData, cx, this.serviceManager);
		csd.start();
	}

	public void saveData() {
		switch (serverData.getAsInteger(KEY_MODE)) {
			case MODE_EDIT:
				updateRecord();
				break;
			case MODE_CREATE:
				if (serverData.get(Servers.FRIENDLY_NAME).equals(""))
					serverData.put(Servers.FRIENDLY_NAME, getString(R.string.UnNamedServer));
				createRecord();
				break;
		}

		if (serverData.getAsInteger(Servers.ACTIVE) == 1) {
			if (Constants.LOG_VERBOSE) Log.v(TAG, "Scheduling HomeSetDiscovery on successful server config.");
			try {
				int serverId = serverData.getAsInteger(Servers._ID);
				ServiceRequest sr = serviceManager.getServiceRequest();
				sr.homeSetDiscovery(serverId);
			} catch (RemoteException e) {
				if (Constants.LOG_VERBOSE)
					Log.v(TAG, "Error starting home set discovery: " + e.getMessage() + " "
							+ Log.getStackTraceString(e));
			} catch (Exception e) {
				Log.e(TAG, Log.getStackTraceString(e));
			}
		}
	}

	public void finishAndClose() {
		setResult(RESULT_OK);
		finish();
	}

	private void createRecord() {
		try {
			Uri result = getContentResolver().insert(Servers.CONTENT_URI, Servers.cloneValidColumns(serverData));
			int id = Integer.parseInt(result.getPathSegments().get(0));
			if (id < 0) throw new Exception("Failed to add server");
			serverData.put(Servers._ID, id);
			serverData.put(KEY_MODE, MODE_EDIT);

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			prefs.edit().putInt(PrefNames.serverIsConfigured, 1).apply();
		} catch (Exception e) {
			serverData.put(KEY_MODE, MODE_CREATE);
			Toast.makeText(this, getString(R.string.errorSavingServerConfig), Toast.LENGTH_LONG).show();
		}
	}

	private void updateRecord() {
		Uri provider = ContentUris.withAppendedId(Servers.CONTENT_URI, serverData.getAsInteger(Servers._ID));
		getContentResolver().update(provider, Servers.cloneValidColumns(serverData), null, null);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.advanced_settings_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.advancedMenuItem) {
			if (iface == INTERFACE_SIMPLE) {
				iface = INTERFACE_ADVANCED;
				if (fragment != null) fragment.rebuild();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean isAdvancedInterface() {
		return iface == INTERFACE_ADVANCED;
	}

	@Override
	public ConnectivityManager getConnectivityService() {
		return (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
	}

	ContentValues getServerData() {
		return serverData;
	}

	void onServerDataChanged() {
		if (!originalServerData.equals(serverData)) apply.setEnabled(true);
	}
}
