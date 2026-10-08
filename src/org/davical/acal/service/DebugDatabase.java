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

package org.davical.acal.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.davical.acal.Constants;
import org.davical.acal.database.AcalDBHelper;

public class DebugDatabase extends ServiceJob {

	public static final String TAG = "aCal DebugDatabase";

	public static final int REVERT = 0;
	public static final int SAVE = 1;

	private int jobtype;
	private Uri saveTarget;
	private aCalService context;

	public DebugDatabase(int jobtype) {
		this.jobtype = jobtype;
		this.TIME_TO_EXECUTE = System.currentTimeMillis();
	}

	/**
	 * Save an unencrypted copy of the database.
	 *
	 * @param saveTarget Where to write it, as chosen by the user with the
	 * system file picker.
	 */
	public DebugDatabase(Uri saveTarget) {
		this(SAVE);
		this.saveTarget = saveTarget;
	}

	@Override
	public void run(aCalService context) {
		this.context = context;
		try {
			switch (this.jobtype) {
			case REVERT: revertDatabase(); break;
			case SAVE:	 saveDatabase(); break;
			default:
				Log.w(TAG, "Unable to execute jobtype - invalid jobtype id provided.");
			}
		} catch (Exception e) {
			Log.e(TAG,"Unknown error executing jobtype: "+e.getMessage());
		}
	}

	private void revertDatabase() {
		if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"Reverting database...");
		try {
			AcalDBHelper dbHelper = new AcalDBHelper(this.context);
			dbHelper.onUpgrade(dbHelper.getWritableDatabase(), -1, -1);
			dbHelper.close();
			if (Constants.LOG_DEBUG) Log.println(Constants.LOGD,TAG,"Reversion complete.");
		} catch (Exception e) {
			Log.e(TAG,"Error reverting database: "+e.getMessage());
		}
	}

	private void saveDatabase() {
		Log.println(Constants.LOGI,TAG, "Database copy requested. Beginning file xfer to "+saveTarget);
		// The database is exported to a plain SQLite file of our own first, as
		// SQLite needs a real file to write to, and that is then copied to
		// wherever the user asked for it.
		File plainCopy = new File(context.getCacheDir(), "acal-export.db");
		String outcome;
		try {
			AcalDBHelper.exportPlainCopy(context, plainCopy);
			try ( InputStream in = new FileInputStream(plainCopy);
					OutputStream out = context.getContentResolver().openOutputStream(saveTarget) ) {
				if ( out == null ) throw new IOException("Could not open "+saveTarget);
				byte[] buffer = new byte[65536];
				int count;
				while ( (count = in.read(buffer)) > 0 ) out.write(buffer, 0, count);
			}
			outcome = "Unencrypted copy of the database saved.";
			Log.println(Constants.LOGI,TAG, "Database copy completed.");
		} catch (Exception e) {
			Log.e(TAG,"Error saving a copy of the database to '"+saveTarget+"'", e);
			outcome = "Saving the database failed: "+e.getMessage();
		}
		finally {
			for ( String suffix : new String[] { "", "-journal", "-wal", "-shm" } ) {
				new File(plainCopy.getPath() + suffix).delete();
			}
		}

		final String message = outcome;
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, message, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public String getDescription() {
		switch( jobtype ) {
			case SAVE:
				return "Saving an unencrypted copy of the database to "+ saveTarget;
			case REVERT:
				return "Reverting to empty database.";
		}
		Log.e(TAG,"No description defined for jobtype "+jobtype );
		return "Unknown DebugDatabase jobtype!";
	}



}
