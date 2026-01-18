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

package com.morphoss.acal.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.PermissionHelper;
import com.morphoss.acal.R;

/**
 * Base activity class for main views that use AppCompat with Navigation Drawer.
 * Provides Toolbar and NavigationDrawer setup, plus permission handling from AcalActivity.
 */
public abstract class AcalAppCompatActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener {

	public static SharedPreferences prefs;

	protected DrawerLayout drawerLayout;
	protected NavigationView navigationView;
	protected Toolbar toolbar;
	protected ActionBarDrawerToggle drawerToggle;

	/**
	 * Flag to track if we're waiting for permission result.
	 * Subclasses can check this to avoid duplicate initialization.
	 */
	protected boolean waitingForPermissions = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		AcalTheme.initializeTheme(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		AcalTheme.initializeTheme(this);
		updateThemeColors();
	}

	/**
	 * Update toolbar and drawer header colors from theme.
	 * Called on resume to pick up any theme changes from Settings.
	 */
	protected void updateThemeColors() {
		if (toolbar != null) {
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
		}
		if (navigationView != null) {
			View headerView = navigationView.getHeaderView(0);
			if (headerView != null) {
				headerView.setBackgroundColor(AcalTheme.getToolbarColour());
			}
		}
	}

	/**
	 * Set up the toolbar and navigation drawer after setContentView is called.
	 * Subclasses should call this in onCreate after setContentView.
	 *
	 * @param title The title to display in the toolbar
	 */
	protected void setupToolbarAndDrawer(String title) {
		toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			// Apply theme color (30% darker than button color)
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
			setSupportActionBar(toolbar);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setTitle(title);
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}
		}

		drawerLayout = findViewById(R.id.drawer_layout);
		navigationView = findViewById(R.id.nav_view);

		if (drawerLayout != null && navigationView != null) {
			// Apply theme color to nav drawer header
			View headerView = navigationView.getHeaderView(0);
			if (headerView != null) {
				headerView.setBackgroundColor(AcalTheme.getToolbarColour());
			}

			drawerToggle = new ActionBarDrawerToggle(
					this,
					drawerLayout,
					toolbar,
					R.string.nav_drawer_open,
					R.string.nav_drawer_close
			);
			drawerLayout.addDrawerListener(drawerToggle);
			drawerToggle.syncState();

			navigationView.setNavigationItemSelectedListener(this);

			// Highlight current destination
			highlightCurrentNavItem();
		}
	}

	/**
	 * Highlight the current navigation item based on the activity class.
	 * Subclasses can override to customize.
	 */
	protected void highlightCurrentNavItem() {
		if (navigationView == null) return;

		if (this instanceof MonthView) {
			navigationView.setCheckedItem(R.id.nav_calendar);
		} else if (this instanceof TodoListView) {
			navigationView.setCheckedItem(R.id.nav_tasks);
		} else if (this instanceof JournalListView) {
			navigationView.setCheckedItem(R.id.nav_notes);
		}
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.nav_calendar) {
			if (!(this instanceof MonthView)) {
				Intent intent = new Intent(this, MonthView.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);
				finish();
			}
		} else if (id == R.id.nav_tasks) {
			if (!(this instanceof TodoListView)) {
				Intent intent = new Intent(this, TodoListView.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);
				finish();
			}
		} else if (id == R.id.nav_notes) {
			if (!(this instanceof JournalListView)) {
				Intent intent = new Intent(this, JournalListView.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);
				finish();
			}
		} else if (id == R.id.nav_settings) {
			startActivity(new Intent(this, Settings.class));
		} else if (id == R.id.nav_about) {
			startActivity(new Intent(this, ShowUpgradeChanges.class));
		}

		drawerLayout.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public void onBackPressed() {
		if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
			drawerLayout.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
		}
	}

	// Permission handling methods copied from AcalActivity

	/**
	 * Check if all required permissions are granted.
	 * Call this at the start of your activity to ensure permissions are available.
	 *
	 * @return true if all permissions are granted, false if a request was made
	 */
	protected boolean checkPermissions() {
		if (PermissionHelper.hasAllPermissions(this)) {
			return true;
		}

		waitingForPermissions = true;

		if (PermissionHelper.shouldShowRationale(this)) {
			showPermissionRationale();
		} else {
			PermissionHelper.requestMissingPermissions(this);
		}
		return false;
	}

	/**
	 * Show a dialog explaining why permissions are needed.
	 */
	protected void showPermissionRationale() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.permission_required_title)
			.setMessage(PermissionHelper.getPermissionRationale())
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					PermissionHelper.requestMissingPermissions(AcalAppCompatActivity.this);
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					onPermissionsDenied();
				}
			})
			.setCancelable(false)
			.show();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		// Handle alarm permission request (POST_NOTIFICATIONS)
		if (requestCode == PermissionHelper.ALARM_PERMISSION_REQUEST_CODE) {
			if (!checkAlarmPermissions()) {
				return;
			}
			onAlarmPermissionsChecked();
			return;
		}

		waitingForPermissions = false;

		if (PermissionHelper.allPermissionsGranted(requestCode, grantResults)) {
			onPermissionsGranted();
		} else {
			onPermissionsDenied();
		}
	}

	/**
	 * Called when all required permissions have been granted.
	 * Override this to continue with your activity's initialization.
	 */
	protected void onPermissionsGranted() {
	}

	/**
	 * Called when the user denies required permissions.
	 * Default behavior shows a message and finishes the activity.
	 */
	protected void onPermissionsDenied() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.permission_denied_title)
			.setMessage(R.string.permission_denied_message)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setCancelable(false)
			.show();
	}

	/**
	 * Check if alarm-related permissions are missing and prompt the user.
	 * Call this after basic permissions are granted.
	 *
	 * @return true if all alarm permissions are granted, false if prompting user
	 */
	protected boolean checkAlarmPermissions() {
		if (PermissionHelper.needsNotificationPermission(this)) {
			showNotificationPermissionDialog();
			return false;
		}

		if (PermissionHelper.needsExactAlarmPermission(this) ||
			PermissionHelper.needsFullScreenIntentPermission(this)) {
			showAlarmSettingsDialog();
			return false;
		}

		return true;
	}

	private void showNotificationPermissionDialog() {
		new AlertDialog.Builder(this)
			.setTitle("Notification Permission Required")
			.setMessage("aCal needs notification permission to show calendar alarms. Without this permission, you won't receive alarm notifications.")
			.setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					PermissionHelper.requestNotificationPermission(AcalAppCompatActivity.this);
				}
			})
			.setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					onAlarmPermissionsChecked();
				}
			})
			.setCancelable(false)
			.show();
	}

	private void showAlarmSettingsDialog() {
		StringBuilder message = new StringBuilder();
		message.append("For alarms to work reliably, aCal needs additional permissions:\n\n");
		message.append(PermissionHelper.getAlarmPermissionStatus(this));
		message.append("\nWould you like to open Settings to enable these?");

		final boolean needsExactAlarm = PermissionHelper.needsExactAlarmPermission(this);

		new AlertDialog.Builder(this)
			.setTitle("Alarm Permissions Required")
			.setMessage(message.toString())
			.setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent;
					if (needsExactAlarm) {
						intent = PermissionHelper.getExactAlarmSettingsIntent(AcalAppCompatActivity.this);
					} else {
						intent = PermissionHelper.getFullScreenIntentSettingsIntent(AcalAppCompatActivity.this);
					}
					if (intent != null) {
						startActivity(intent);
					}
				}
			})
			.setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					onAlarmPermissionsChecked();
				}
			})
			.setCancelable(false)
			.show();
	}

	/**
	 * Called after alarm permissions have been checked/requested.
	 * Override this if you need to do something after the alarm permission flow.
	 */
	protected void onAlarmPermissionsChecked() {
	}
}
