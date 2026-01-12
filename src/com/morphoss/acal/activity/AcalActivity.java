package com.morphoss.acal.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
}
