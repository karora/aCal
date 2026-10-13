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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import org.davical.acal.AcalTheme;
import org.davical.acal.R;
import org.davical.acal.ServiceManager;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.requests.RRCollectionData;
import org.davical.acal.database.resourcesmanager.requests.RRImportItems;
import org.davical.acal.dataservice.Collection;
import org.davical.acal.providers.DavCollections;
import org.davical.acal.service.ServiceJob;
import org.davical.acal.service.ServiceRequest;
import org.davical.acal.service.SyncChangesToServer;
import org.davical.acal.service.WorkerClass;
import org.davical.acal.storage.IcsFile;
import org.davical.acal.storage.LocalCalendars;
import org.davical.acal.storage.StorageBackend;
import org.davical.acal.storage.StorageBackends;

/**
 * <p>This activity allows the user to configure a single collection. It MUST be handed a ContentValues object with as
 * an extra on the intent using key "CollectionData".</p>
 *
 * <p>The ContentValues object MUST have a value with the key
 * CollectionConfiguration.MODEKEY. If it is set to MODE_CREATE it must also contain all the fields for a collection
 * as defined by the dav_collection table schema. Failure to provide required details of the required type
 * may cause unexpected behaviour.</p>
 *
 * <p>This configuration screen uses AndroidX PreferenceFragmentCompat but does not persist data to
 * SharedPreferences — data is persisted manually via the ContentResolver on apply.</p>
 */
public class CollectionConfiguration extends AcalAppCompatActivity implements OnClickListener {

	public static final String TAG = "aCal CollectionConfiguration";

	private ContentValues collectionData;
	private ContentValues originalCollectionData;

	private Button apply;
	private Button cancel;

	private ServiceManager serviceManager;
	/** True if the collection does not exist yet, and saving will make it */
	private boolean isNew = false;
	/** True if the collection is one kept on the phone, with no server behind it */
	private boolean isLocal = false;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.collection_config);

		toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
			setSupportActionBar(toolbar);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}
			toolbar.setNavigationOnClickListener(v -> finish());
		}

		apply = findViewById(R.id.CollectionConfigApplyButton);
		apply.setOnClickListener(this);
		apply.setEnabled(false);
		cancel = findViewById(R.id.CollectionConfigCancelButton);
		cancel.setOnClickListener(this);

		try {
			collectionData = getIntent().getExtras().getParcelable("CollectionData");
		} catch (Exception e) {
			finish();
			return;
		}
		if (collectionData == null) {
			finish();
			return;
		}

		// With no id it is a collection to be made, and the only kind made here
		// is one kept on the phone.  Any other comes from its server.
		isNew = !collectionData.containsKey(DavCollections._ID);
		isLocal = LocalCalendars.isLocal(collectionData, getContentResolver());
		if (isNew && !isLocal) {
			Log.e(TAG, "CollectionConfiguration called with incorrect data.");
			finish();
			return;
		}
		if (isNew) apply.setEnabled(true);

		originalCollectionData = new ContentValues();
		originalCollectionData.putAll(collectionData);

		if (getSupportActionBar() != null) {
			String name = collectionData.getAsString(DavCollections.DISPLAYNAME);
			getSupportActionBar().setTitle(isNew ? getString(R.string.AddLocalCalendar)
					: (name != null && !name.isEmpty() ? name : getString(R.string.appActivityCollectionConfigList)));
		}

		if (bundle == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.collection_config_container, new CollectionConfigurationFragment())
					.commit();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (serviceManager != null) serviceManager.close();
		serviceManager = null;
	}

	@Override
	public void onResume() {
		super.onResume();
		serviceManager = new ServiceManager(this);
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == apply.getId()) applyButton();
		else if (v.getId() == cancel.getId()) cancelButton();
	}

	private void cancelButton() {
		finish();
	}

	private void applyButton() {
		if ((collectionData.getAsInteger(DavCollections.ACTIVE_ADDRESSBOOK) != null && 1 == collectionData.getAsInteger(DavCollections.ACTIVE_ADDRESSBOOK))
				|| (collectionData.getAsInteger(DavCollections.ACTIVE_EVENTS) != null && 1 == collectionData.getAsInteger(DavCollections.ACTIVE_EVENTS))
				|| (collectionData.getAsInteger(DavCollections.ACTIVE_TASKS) != null && 1 == collectionData.getAsInteger(DavCollections.ACTIVE_TASKS))
				|| (collectionData.getAsInteger(DavCollections.ACTIVE_JOURNAL) != null && 1 == collectionData.getAsInteger(DavCollections.ACTIVE_JOURNAL))) {
			checkCollection();
		} else {
			saveData();
			finish();
		}
	}

	private void checkCollection() {
		saveData();
		finish();
	}

	private void saveData() {
		if (isNew) {
			Uri inserted = getContentResolver().insert(DavCollections.CONTENT_URI, collectionData);
			collectionData.put(DavCollections._ID, Integer.parseInt(inserted.getLastPathSegment()));
			isNew = false;
		}
		else {
			Uri provider = ContentUris.withAppendedId(DavCollections.CONTENT_URI, collectionData.getAsInteger(DavCollections._ID));
			getContentResolver().update(provider, collectionData, null, null);
		}
		Collection.flush();

		Intent res = new Intent();
		res.putExtra("UpdateRequired", collectionData.getAsInteger(DavCollections._ID));
		setResult(RESULT_OK, res);

		ServiceJob job;
		if (collectionData.getAsInteger(DavCollections.SYNC_METADATA) != null
				&& collectionData.getAsInteger(DavCollections.SYNC_METADATA) == 1)
			job = new SyncChangesToServer();
		else {
			StorageBackend backend = StorageBackends.forServer(
						collectionData.getAsInteger(DavCollections.SERVER_ID), getContentResolver());
			job = (backend == null ? null
						: backend.refreshCollection(collectionData.getAsInteger(DavCollections._ID), false));
		}

		WorkerClass worker = WorkerClass.getExistingInstance();
		if ( job != null && worker != null ) worker.addJobAndWake(job);
	}

	boolean isNew() {
		return isNew;
	}

	// ---- A copy of the calendar as a file, and a file brought in

	/** A file bigger than this is not a calendar anyone keeps on a phone. */
	private static final int LARGEST_IMPORT = 32 * 1024 * 1024;

	private final ActivityResultLauncher<String> exportPicker = registerForActivityResult(
			new ActivityResultContracts.CreateDocument("text/calendar"),
			target -> { if (target != null) exportTo(target); });

	private final ActivityResultLauncher<String[]> importPicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			source -> { if (source != null) importFrom(source); });

	void chooseFileToExportTo() {
		String name = collectionData.getAsString(DavCollections.DISPLAYNAME);
		if (name == null || name.trim().isEmpty()) name = "calendar";
		// Nothing in the name that could not be in the name of a file
		exportPicker.launch(name.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") + ".ics");
	}

	void chooseFileToImport() {
		// Not every file manager knows a calendar when it sees one
		importPicker.launch(new String[] { "text/calendar", "application/ics", "text/x-vcalendar", "*/*" });
	}

	private void exportTo(final Uri target) {
		final Context context = getApplicationContext();
		final long collectionId = collectionData.getAsLong(DavCollections._ID);
		new Thread(() -> {
			String message;
			try {
				ArrayList<String> resources = ResourceManager.getInstance(context)
						.sendBlockingRequest(new RRCollectionData(collectionId)).result();
				byte[] file = IcsFile.export(resources).getBytes(StandardCharsets.UTF_8);
				// "wt" so that a file being replaced is not left with the end of the old one
				try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
					if (out == null) throw new IOException("Could not open " + target);
					out.write(file);
				}
				int count = IcsFile.count(resources);
				message = getResources().getQuantityString(R.plurals.ExportedCount, count, count);
			}
			catch (Exception e) {
				Log.e(TAG, "Could not export collection " + collectionId + " to " + target, e);
				message = getString(R.string.ExportFailed);
			}
			final String say = message;
			runOnUiThread(() -> Toast.makeText(context, say, Toast.LENGTH_LONG).show());
		}, "aCal export").start();
	}

	private void importFrom(final Uri source) {
		final Context context = getApplicationContext();
		final long collectionId = collectionData.getAsLong(DavCollections._ID);
		new Thread(() -> {
			String message;
			try {
				ByteArrayOutputStream file = new ByteArrayOutputStream();
				try (InputStream in = context.getContentResolver().openInputStream(source)) {
					if (in == null) throw new IOException("Could not open " + source);
					byte[] buffer = new byte[65536];
					int got;
					while ((got = in.read(buffer)) > 0) {
						file.write(buffer, 0, got);
						if (file.size() > LARGEST_IMPORT) throw new IOException("The file is too large");
					}
				}
				List<IcsFile.Item> items = IcsFile.split(new String(file.toByteArray(), StandardCharsets.UTF_8));
				if (items.isEmpty()) {
					message = getString(R.string.ImportNothingFound);
				}
				else {
					RRImportItems.Counts counts = ResourceManager.getInstance(context)
							.sendBlockingRequest(new RRImportItems(collectionId, items)).result();
					message = getString(R.string.ImportedCounts, counts.added, counts.replaced, counts.unchanged);
				}
			}
			catch (Exception e) {
				Log.e(TAG, "Could not import " + source + " into collection " + collectionId, e);
				message = getString(R.string.ImportFailed);
			}
			final String say = message;
			runOnUiThread(() -> Toast.makeText(context, say, Toast.LENGTH_LONG).show());
		}, "aCal import").start();
	}

	boolean isLocal() {
		return isLocal;
	}

	/**
	 * Delete this collection, which is one kept on the phone, once the user
	 * has been told what that means and has agreed.
	 */
	void confirmDelete() {
		if (!isLocal || isNew) return;
		final Integer collectionId = collectionData.getAsInteger(DavCollections._ID);
		if (collectionId == null) return;
		new AlertDialog.Builder(this)
			.setTitle(R.string.DeleteLocalCalendar)
			.setMessage(getString(R.string.DeleteLocalCalendarConfirm, collectionData.getAsString(DavCollections.DISPLAYNAME)))
			.setPositiveButton(R.string.Delete, (dialog, which) -> {
				LocalCalendars.delete(getApplicationContext(), collectionId);
				setResult(RESULT_OK, new Intent());
				finish();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	ContentValues getCollectionData() {
		return collectionData;
	}

	/**
	 * Ask the service to synchronise this collection immediately.
	 *
	 * @param fullResync false requests an immediate incremental sync (sync-report / ctag
	 *                   check); true rebuilds the local resource list from the server,
	 *                   which also removes anything deleted on the server.
	 */
	void requestSync(boolean fullResync) {
		Integer collectionId = collectionData.getAsInteger(DavCollections._ID);
		if (collectionId == null) return;
		try {
			ServiceRequest sr = (serviceManager == null ? null : serviceManager.getServiceRequest());
			if (sr == null) {
				Toast.makeText(this, getString(R.string.Sync_request_failed), Toast.LENGTH_SHORT).show();
				return;
			}
			if (fullResync) sr.fullCollectionResync(collectionId);
			else sr.syncCollectionNow(collectionId);
			Toast.makeText(this, getString(R.string.Synchronisation_requested), Toast.LENGTH_SHORT).show();
		} catch (RemoteException e) {
			Log.e(TAG, "Unable to send synchronisation request to service: " + e.getMessage());
			Toast.makeText(this, getString(R.string.Sync_request_failed), Toast.LENGTH_SHORT).show();
		}
	}

	void onCollectionDataChanged() {
		if (!originalCollectionData.equals(collectionData)) apply.setEnabled(true);
	}
}
