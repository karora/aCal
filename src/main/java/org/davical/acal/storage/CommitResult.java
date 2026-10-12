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
 * What became of an attempt to commit one pending change.
 */
public final class CommitResult {

	public enum Outcome {
		/** The change is where it belongs, and the pending change has been retired. */
		APPLIED,
		/** It could not be done this time, but may be another time. */
		RETRY,
		/** It was refused, and trying the same thing again will get the same answer. */
		REFUSED
	}

	public final Outcome outcome;

	/** The status the storage answered with, or 0 if there was no answer. */
	public final int status;

	/** True if the collection should be brought up to date afterwards. */
	public final boolean refreshCollection;

	public CommitResult(Outcome outcome, int status, boolean refreshCollection) {
		this.outcome = outcome;
		this.status = status;
		this.refreshCollection = refreshCollection;
	}
}
