/*
 * Copyright (C) 2026 Andrew McMillan
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

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.davical.acal.AcalTheme;
import org.davical.acal.Constants;
import org.davical.acal.R;
import org.davical.acal.acaltime.AcalDateTimeFormatter;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.requests.RRRefusedChanges;
import org.davical.acal.database.resourcesmanager.requests.RRResolvePendingChange;
import org.davical.acal.dataservice.Collection;
import org.davical.acal.service.SyncChangesToServer;
import org.davical.acal.service.WorkerClass;

/**
 * Lists the changes the server refused, which are kept until the user says
 * whether each should be tried again or discarded.  Reached from the
 * notification that is shown while there are any.
 */
public class UnsavedChangesActivity extends AcalAppCompatActivity {

	public static final String TAG = "aCal UnsavedChanges";

	/** One row of the list: the change, and what could only be looked up off the main thread. */
	private static class Row {
		final UnsavedChange change;
		final String collectionName;

		Row(UnsavedChange change, String collectionName) {
			this.change = change;
			this.collectionName = collectionName;
		}
	}

	private TextView intro;
	private ViewGroup list;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.unsaved_changes);

		toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
			setSupportActionBar(toolbar);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
				getSupportActionBar().setTitle(R.string.unsaved_title);
			}
			toolbar.setNavigationOnClickListener(v -> finish());
		}

		intro = findViewById(R.id.UnsavedIntro);
		list = findViewById(R.id.UnsavedList);
	}

	@Override
	protected void onResume() {
		super.onResume();
		load();
	}

	/**
	 * Fetch the refused changes and show them.  The database is not to be
	 * waited for on the main thread.
	 */
	private void load() {
		final Context context = getApplicationContext();
		new Thread(() -> {
			final List<Row> rows = new ArrayList<Row>();
			try {
				for ( ContentValues pending : ResourceManager.getInstance(context)
							.sendBlockingRequest(new RRRefusedChanges()).result() ) {
					UnsavedChange change = new UnsavedChange(pending);
					String collectionName = null;
					try {
						collectionName = Collection.getInstance(change.collectionId, context).getDisplayName();
					}
					catch ( Exception e ) {
						// A collection we cannot name is no reason to hide the change
					}
					rows.add(new Row(change, collectionName));
				}
			}
			catch ( Exception e ) {
				Log.e(TAG, "Could not fetch the refused changes", e);
			}
			updateNotification(context, rows.size());
			runOnUiThread(() -> show(rows));
		}, "aCal UnsavedChanges load").start();
	}

	private void show(List<Row> rows) {
		if ( isFinishing() || isDestroyed() ) return;

		intro.setText(rows.isEmpty() ? R.string.unsaved_none : R.string.unsaved_intro);
		list.removeAllViews();

		for ( final Row row : rows ) {
			final UnsavedChange change = row.change;
			View view = getLayoutInflater().inflate(R.layout.unsaved_change_row, list, false);

			((TextView) view.findViewById(R.id.UnsavedTitle)).setText(
					change.title == null ? getString(R.string.unsaved_untitled) : change.title);

			StringBuilder detail = new StringBuilder(getString(change.kind.label));
			if ( row.collectionName != null ) detail.append(" · ").append(row.collectionName);
			if ( change.start != null ) {
				detail.append('\n').append(AcalDateTimeFormatter.fmtFull(change.start, DateFormat.is24HourFormat(this)));
			}
			((TextView) view.findViewById(R.id.UnsavedDetail)).setText(detail);

			((TextView) view.findViewById(R.id.UnsavedReason)).setText(getString(change.reason(), change.status));

			view.findViewById(R.id.UnsavedTryAgain).setOnClickListener(v -> resolve(change, true));
			view.findViewById(R.id.UnsavedDiscard).setOnClickListener(v ->
				new AlertDialog.Builder(this)
					.setTitle(R.string.unsaved_discard_title)
					.setMessage(change.kind.discardWarning)
					.setPositiveButton(R.string.unsaved_discard, (dialog, which) -> resolve(change, false))
					.setNegativeButton(android.R.string.cancel, null)
					.show());

			list.addView(view);
		}
	}

	private void resolve(final UnsavedChange change, final boolean tryAgain) {
		final Context context = getApplicationContext();
		new Thread(() -> {
			try {
				ResourceManager.getInstance(context)
						.sendBlockingRequest(new RRResolvePendingChange(change.pendingId, tryAgain));
				if ( tryAgain ) {
					WorkerClass worker = WorkerClass.getExistingInstance();
					if ( worker != null ) worker.addJobAndWake(new SyncChangesToServer());
				}
			}
			catch ( Exception e ) {
				Log.e(TAG, "Could not resolve pending change " + change.pendingId, e);
			}
			runOnUiThread(this::load);
		}, "aCal UnsavedChanges resolve").start();
	}


	/**
	 * Show, update or take away the notification that says there are refused
	 * changes.  It makes a sound only when it first appears.
	 */
	public static void updateNotification(Context context, int refused) {
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if ( nm == null ) return;
		if ( refused < 1 ) {
			nm.cancel(Constants.UNSAVED_CHANGES_NOTIFICATION_ID);
			return;
		}

		Intent open = new Intent(context, UnsavedChangesActivity.class);
		open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent pi = PendingIntent.getActivity(context, Constants.UNSAVED_CHANGES_NOTIFICATION_ID, open,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		String text = context.getString(R.string.unsaved_notif_text);
		Notification.Builder builder = new Notification.Builder(context)
				.setSmallIcon(R.drawable.icon)
				.setContentTitle(context.getResources().getQuantityString(R.plurals.unsaved_notif_title, refused, refused))
				.setContentText(text)
				.setStyle(new Notification.BigTextStyle().bigText(text))
				.setContentIntent(pi)
				.setOnlyAlertOnce(true)
				.setAutoCancel(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setChannelId(Constants.STATUS_NOTIFICATION_CHANNEL_ID);
		}
		nm.notify(Constants.UNSAVED_CHANGES_NOTIFICATION_ID, builder.build());
	}
}
