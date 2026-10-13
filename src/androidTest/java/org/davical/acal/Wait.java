package org.davical.acal;

import static org.junit.Assert.fail;

import java.util.concurrent.Callable;

import org.davical.acal.database.TestDatabase;

/**
 * Saving and synchronising happen on the app's own threads, so a test waits
 * for what it expects to become true.
 */
public final class Wait {

	private static final long LONGEST = 30000;

	private Wait() { }

	public static void until( String what, Callable<Boolean> condition ) throws Exception {
		long giveUp = System.currentTimeMillis() + LONGEST;
		Exception last = null;
		while( System.currentTimeMillis() < giveUp ) {
			try {
				if ( condition.call() ) return;
				last = null;
			}
			catch( android.database.sqlite.SQLiteException busy ) {
				// Something is being written: look again
				last = busy;
			}
			Thread.sleep(100);
		}
		fail("Waited " + (LONGEST / 1000) + " seconds and still not true: " + what + (last == null ? "" : " (" + last + ")"));
	}

	public static void untilThereAre( final long expected, final String table, final String where ) throws Exception {
		until(expected + " rows in " + table + (where == null ? "" : " where " + where), new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return TestDatabase.countNow(table, where) == expected;
			}
		});
	}

	public static void untilThereAreNone( String table, String where ) throws Exception {
		untilThereAre(0, table, where);
	}
}
