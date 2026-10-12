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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RetryPolicyTest {

	private static final long SECOND = 1000L;
	private static final long MINUTE = 60 * SECOND;
	private static final long NOW = 1791800000000L;

	@Test
	public void aChangeNeverTriedIsDue() {
		assertEquals(0, RetryPolicy.waitAfter(0));
		assertTrue(RetryPolicy.isDue(0, null, NOW));
		assertTrue(RetryPolicy.isDue(3, null, NOW));
	}

	@Test
	public void theWaitDoublesWithEachAttempt() {
		assertEquals(90 * SECOND, RetryPolicy.waitAfter(1));
		assertEquals(3 * MINUTE, RetryPolicy.waitAfter(2));
		assertEquals(6 * MINUTE, RetryPolicy.waitAfter(3));
		assertEquals(12 * MINUTE, RetryPolicy.waitAfter(4));
		assertEquals(24 * MINUTE, RetryPolicy.waitAfter(5));
		assertEquals(48 * MINUTE, RetryPolicy.waitAfter(6));
	}

	@Test
	public void theWaitIsNeverMoreThanAnHour() {
		for ( int attempts : new int[] { 7, 8, 30, 64, 1000, Integer.MAX_VALUE } ) {
			assertEquals("after " + attempts, 60 * MINUTE, RetryPolicy.waitAfter(attempts));
		}
	}

	@Test
	public void aChangeIsDueOnceItsWaitIsOver() {
		assertFalse(RetryPolicy.isDue(1, NOW, NOW));
		assertFalse(RetryPolicy.isDue(1, NOW - 89 * SECOND, NOW));
		assertTrue(RetryPolicy.isDue(1, NOW - 90 * SECOND, NOW));

		assertFalse(RetryPolicy.isDue(2, NOW - 90 * SECOND, NOW));
		assertTrue(RetryPolicy.isDue(2, NOW - 3 * MINUTE, NOW));

		assertEquals(NOW + 3 * MINUTE, RetryPolicy.dueAt(2, NOW));
	}

	/** Or a change tried just before the clock was put back a year would wait a year. */
	@Test
	public void aLastAttemptInTheFutureDoesNotHoldAChangeBack() {
		assertTrue(RetryPolicy.isDue(5, NOW + 24 * 60 * MINUTE, NOW));
	}
}
