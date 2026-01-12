package com.morphoss.acal.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.PermissionHelper;
import com.morphoss.acal.R;

public abstract class AcalActivity extends Activity {

	public static SharedPreferences prefs;

	/**
	 * Flag to track if we're waiting for permission result.
	 * Subclasses can check this to avoid duplicate initialization.
	 */
	protected boolean waitingForPermissions = false;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		AcalTheme.initializeTheme(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		AcalTheme.initializeTheme(this);
	}

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
					PermissionHelper.requestMissingPermissions(AcalActivity.this);
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
			// Continue with alarm permission check flow
			// (will check for other special permissions next)
			if (!checkAlarmPermissions()) {
				return; // Still prompting for permissions
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
		// Default implementation does nothing.
		// Subclasses should override to continue initialization.
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
		// First check notification permission (runtime permission on Android 13+)
		if (PermissionHelper.needsNotificationPermission(this)) {
			showNotificationPermissionDialog();
			return false;
		}

		// Then check special permissions that require Settings
		if (PermissionHelper.needsExactAlarmPermission(this) ||
			PermissionHelper.needsFullScreenIntentPermission(this)) {
			showAlarmSettingsDialog();
			return false;
		}

		return true;
	}

	/**
	 * Show dialog to request notification permission.
	 */
	private void showNotificationPermissionDialog() {
		new AlertDialog.Builder(this)
			.setTitle("Notification Permission Required")
			.setMessage("aCal needs notification permission to show calendar alarms. Without this permission, you won't receive alarm notifications.")
			.setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					PermissionHelper.requestNotificationPermission(AcalActivity.this);
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
	 * Show dialog to guide user to Settings for special permissions.
	 */
	private void showAlarmSettingsDialog() {
		StringBuilder message = new StringBuilder();
		message.append("For alarms to work reliably, aCal needs additional permissions:\n\n");
		message.append(PermissionHelper.getAlarmPermissionStatus(this));
		message.append("\nWould you like to open Settings to enable these?");

		final boolean needsExactAlarm = PermissionHelper.needsExactAlarmPermission(this);
		final boolean needsFullScreen = PermissionHelper.needsFullScreenIntentPermission(this);

		new AlertDialog.Builder(this)
			.setTitle("Alarm Permissions Required")
			.setMessage(message.toString())
			.setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Open the most relevant settings page
					Intent intent;
					if (needsExactAlarm) {
						intent = PermissionHelper.getExactAlarmSettingsIntent(AcalActivity.this);
					} else {
						intent = PermissionHelper.getFullScreenIntentSettingsIntent(AcalActivity.this);
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
		// Default implementation does nothing.
	}
}
