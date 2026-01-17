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

package com.morphoss.acal.activity;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.ServiceManager;
import com.morphoss.acal.ServiceManagerCallBack;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.AcalAuthenticator;
import com.morphoss.acal.service.ServiceRequest;

/**
 * <h3>Collection Configuration List - A list of collections that can be configured</h3>
 *
 * <p>
 * This class generates and displays the list of collections available in the dav_collection table. Selecting
 * a collection will start the CollectionConfig activity.
 * </p>
 *
 * @author Morphoss Ltd
 *
 */
public class CollectionConfigList extends AppCompatActivity
		implements CollectionConfigListFragment.OnCollectionSelectedListener {

	public static final String TAG = "aCal CollectionConfigList";

	// Context Menu Options
	public static final int CONTEXT_SYNC_NOW = 1;
	public static final int CONTEXT_DISABLE = 2;
	private static final int CONTEXT_FORCE_FULL_RESYNC = 3;

	public static final int UPDATE_COLLECTION_CONFIG = 0;
	private boolean updateRequested = false;
	private int updateId = -1;

	private ServiceManager serviceManager = null;
	private CollectionConfigListFragment fragment;

	// Needed for AcalAuthenticator
	public static final String ACTION_CHOOSE_ADDRESSBOOK = "com.morphoss.acal.ACTION_CHOOSE_ADDRESSBOOK";

	/**
	 * Get the list of collections and create the list view.
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 * @author Morphoss Ltd
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preferences);

		if (savedInstanceState == null) {
			fragment = new CollectionConfigListFragment();
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.preferences_container, fragment)
					.commit();
		} else {
			fragment = (CollectionConfigListFragment) getSupportFragmentManager()
					.findFragmentById(R.id.preferences_container);
		}

		// Register for context menu on the preference list
		getSupportFragmentManager().executePendingTransactions();
	}

	@Override
	public void onCollectionSelected(int collectionId, ContentValues collectionValues) {
		Intent collectionConfigIntent = new Intent();
		collectionConfigIntent.setClassName("com.morphoss.acal", "com.morphoss.acal.activity.CollectionConfiguration");
		collectionConfigIntent.putExtra("CollectionData", collectionValues);
		startActivityForResult(collectionConfigIntent, UPDATE_COLLECTION_CONFIG);
	}

	@Override
	public void onAccountCreationRequested(int collectionId) {
		createAuthenticatedAccount(collectionId);
	}

	/**
	 * <p>
	 * Called when a user selects 'Sync Now' from the context menu. Schedules an immediate sync
	 * for this collection.
	 * </p>
	 *
	 * @param collectionId The collection ID to synchronise
	 * @param fullCollectionResync Whether to force a full resync
	 * @return true if operation was successful
	 *
	 * @author Morphoss Ltd
	 */
	private boolean syncCollection(int collectionId, boolean fullCollectionResync) {
		try {
			if (serviceManager == null) serviceManager = new ServiceManager(this);
			if (fullCollectionResync) {
				serviceManager.getServiceRequest().fullCollectionResync(collectionId);
			} else {
				serviceManager.getServiceRequest().syncCollectionNow(collectionId);
			}
			return true;
		} catch (RemoteException re) {
			Log.e(TAG, "Unable to send synchronisation request to service: " + re.getMessage());
			Toast.makeText(CollectionConfigList.this, "Request failed: " + re.getMessage(), Toast.LENGTH_SHORT).show();
		}
		return false;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == UPDATE_COLLECTION_CONFIG && resultCode == RESULT_OK) {
			if (data.hasExtra("UpdateRequired")) {
				int cId = data.getIntExtra("UpdateRequired", -1);
				if (cId < 0) return;
				updateId = cId;
				updateRequested = true;
			}
		}
	}

	/**
	 * <p>
	 * Called when a user selects 'Disable Collection' from the context menu.
	 * </p>
	 *
	 * @param collectionId The collection ID to disable
	 * @return true if operation was successful
	 *
	 * @author Morphoss Ltd
	 */
	private boolean disableCollection(int collectionId) {
		return DavCollections.collectionEnabled(false, collectionId, getContentResolver());
	}

	public void createAuthenticatedAccount(int collectionId) {
		ContentValues collectionValues = fragment.getCollectionData(collectionId);
		int serverId = collectionValues.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverValues = Servers.getRow(serverId, getContentResolver());
		String collectionName = collectionValues.getAsString(DavCollections.DISPLAYNAME);
		String serverName = serverValues.getAsString(Servers.FRIENDLY_NAME);
		Account account = new Account(serverName + " - " + collectionName, getString(R.string.AcalAccountType));
		Bundle userData = new Bundle();
		userData.putString(AcalAuthenticator.SERVER_ID, serverValues.getAsString(Servers._ID));
		userData.putString(AcalAuthenticator.COLLECTION_ID, collectionValues.getAsString(DavCollections._ID));
		userData.putString(AcalAuthenticator.USERNAME, serverValues.getAsString(Servers.USERNAME));
		AccountManager am = AccountManager.get(this);
		boolean accountCreated = false;
		try {
			accountCreated = am.addAccountExplicitly(account, "", userData);
		} catch (Exception e) {
			Log.println(Constants.LOGD, TAG, Log.getStackTraceString(e));
		}

		Intent creator = getIntent();
		Bundle extras = creator.getExtras();
		if (accountCreated && extras != null) {
			AccountAuthenticatorResponse response = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
			Bundle result = new Bundle();
			result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
			result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.AcalAccountType));
			response.onResult(result);
		}
		finish();
	}

	/**
	 * <P>
	 * Handles context menu clicks
	 * </P>
	 *
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 * @author Morphoss Ltd
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		try {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			int id = fragment.preferenceListIds[info.position];
			if (Constants.LOG_DEBUG)
				Log.println(Constants.LOGD, TAG, "Context menu on preferenceItem " + info.position + " which I reckon is id " + id);
			switch (item.getItemId()) {
				case CONTEXT_SYNC_NOW:
					return syncCollection(id, false);
				case CONTEXT_DISABLE:
					return disableCollection(id);
				case CONTEXT_FORCE_FULL_RESYNC:
					return syncCollection(id, true);
				default:
					return false;
			}
		} catch (ClassCastException e) {
			return false;
		}
	}

	/**
	 * Creates the context menus for each item in the list.
	 *
	 * @author Morphoss Ltd
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
		menu.setHeaderTitle(getString(R.string.Collection_Options));
		menu.add(0, CollectionConfigList.CONTEXT_SYNC_NOW, 0, getString(R.string.Sync_collection_now));
		menu.add(0, CollectionConfigList.CONTEXT_DISABLE, 0, getString(R.string.Disable_collection));
		menu.add(0, CollectionConfigList.CONTEXT_FORCE_FULL_RESYNC, 0, getString(R.string.Force_full_resync));
	}

	/**
	 * Whenever this activity is resumed, update the collection list as it may have changed.
	 */
	protected void onResume() {
		super.onResume();

		if (updateRequested) {
			Log.println(Constants.LOGI, TAG, "Collection updated: " + updateId);
			updateRequested = false;
			if (updateId > 0) {
				try {
					if (serviceManager != null) serviceManager.close();
				} catch (Exception e) {
				}
				this.serviceManager = new ServiceManager(this, new ServiceManagerCallBack() {

					@Override
					public void serviceConnected(ServiceRequest serviceRequest) {
						try {
							serviceRequest.fullCollectionResync(updateId);
						} catch (RemoteException e) {
							Log.w(TAG, Log.getStackTraceString(e));
						}
					}

				});
				if (fragment != null) {
					fragment.refresh();
				}
			}
			updateId = -1;
		}
		if (this.serviceManager == null) serviceManager = new ServiceManager(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (this.serviceManager != null) this.serviceManager.close();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (this.serviceManager != null) this.serviceManager.close();
		serviceManager = null;
	}
}
