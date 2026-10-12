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
package org.davical.acal.storage;

/**
 * When a pending change that could not be committed is tried again.  The wait
 * doubles with each attempt, so that a server which is down, or which will
 * not take our password, is not asked the same thing every minute for ever.
 */
public final class RetryPolicy {

	public static final long FIRST_WAIT = 90 * 1000L;
	public static final long LONGEST_WAIT = 60 * 60 * 1000L;

	private RetryPolicy() {
	}

	/**
	 * @param attempts how many times the change has been tried
	 * @return how long to wait after the last of them, in milliseconds
	 */
	public static long waitAfter(int attempts) {
		if ( attempts < 1 ) return 0;
		// 90 seconds doubled six times is past the longest wait
		if ( attempts > 6 ) return LONGEST_WAIT;
		return Math.min(FIRST_WAIT << (attempts - 1), LONGEST_WAIT);
	}

	/**
	 * @param lastTried when the last attempt was, or null if there has been none
	 * @return the time from which the change may be tried again
	 */
	public static long dueAt(int attempts, Long lastTried) {
		if ( lastTried == null ) return 0;
		return lastTried + waitAfter(attempts);
	}

	public static boolean isDue(int attempts, Long lastTried, long now) {
		// A time in the future is a clock that has been put back, not a reason to wait
		if ( lastTried != null && lastTried > now ) return true;
		return dueAt(attempts, lastTried) <= now;
	}
}
