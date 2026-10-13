package org.davical.acal;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

/**
 * The instrumented tests delete and replace aCal's database, so they must
 * never run where it holds somebody's data.  A device the tests have used is
 * marked as theirs; one with a database and no mark is refused, before
 * anything has been touched.
 */
public class AcalTestRunner extends AndroidJUnitRunner {

	static final String MARK = "instrumented-tests-own-this-data";

	@Override
	public void onCreate(Bundle arguments) {
		Context context = getTargetContext();
		File mark = new File(context.getNoBackupFilesDir(), MARK);
		if ( !mark.exists() ) {
			if ( context.getDatabasePath("acal.db").exists() ) {
				throw new IllegalStateException("aCal has data on this device which these tests would destroy."
						+ "  Run them on an emulator, or uninstall aCal from this device first.");
			}
			try {
				mark.createNewFile();
			}
			catch( IOException e ) {
				throw new IllegalStateException("Could not mark this device as one for tests", e);
			}
		}
		super.onCreate(arguments);
	}
}
