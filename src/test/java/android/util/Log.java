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

package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Stands in for the real android.util.Log in JVM unit tests, where the
 * methods of the Android framework throw.  Almost all of the app logs, so
 * without this very little of it could be tested off a device.
 *
 * Warnings and errors go to stderr, where they show up in the test report.
 * Anything less is dropped.
 */
public final class Log {

	public static final int VERBOSE = 2;
	public static final int DEBUG = 3;
	public static final int INFO = 4;
	public static final int WARN = 5;
	public static final int ERROR = 6;
	public static final int ASSERT = 7;

	private Log() { }

	public static int v(String tag, String msg) { return println(VERBOSE, tag, msg); }
	public static int v(String tag, String msg, Throwable tr) { return println(VERBOSE, tag, msg, tr); }
	public static int d(String tag, String msg) { return println(DEBUG, tag, msg); }
	public static int d(String tag, String msg, Throwable tr) { return println(DEBUG, tag, msg, tr); }
	public static int i(String tag, String msg) { return println(INFO, tag, msg); }
	public static int i(String tag, String msg, Throwable tr) { return println(INFO, tag, msg, tr); }
	public static int w(String tag, String msg) { return println(WARN, tag, msg); }
	public static int w(String tag, String msg, Throwable tr) { return println(WARN, tag, msg, tr); }
	public static int w(String tag, Throwable tr) { return println(WARN, tag, "", tr); }
	public static int e(String tag, String msg) { return println(ERROR, tag, msg); }
	public static int e(String tag, String msg, Throwable tr) { return println(ERROR, tag, msg, tr); }
	public static int wtf(String tag, String msg) { return println(ASSERT, tag, msg); }
	public static int wtf(String tag, String msg, Throwable tr) { return println(ASSERT, tag, msg, tr); }

	public static boolean isLoggable(String tag, int level) {
		return level >= WARN;
	}

	public static String getStackTraceString(Throwable tr) {
		if ( tr == null ) return "";
		StringWriter sw = new StringWriter();
		tr.printStackTrace(new PrintWriter(sw, true));
		return sw.toString();
	}

	public static int println(int priority, String tag, String msg) {
		if ( priority < WARN ) return 0;
		String line = "WEA".charAt(Math.min(priority, ASSERT) - WARN) + "/" + tag + ": " + msg;
		System.err.println(line);
		return line.length();
	}

	private static int println(int priority, String tag, String msg, Throwable tr) {
		return println(priority, tag, msg + '\n' + getStackTraceString(tr));
	}
}
