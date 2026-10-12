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
package org.davical.acal.database.resourcesmanager.requests;

import org.davical.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceProcessingException;
import org.davical.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;

/**
 * Does what the user chose for a pending change that was refused: try it
 * again, or discard it.
 */
public class RRResolvePendingChange implements BlockingResourceRequest {

	private final long pendingId;
	private final boolean tryAgain;
	private boolean isProcessed = false;

	public RRResolvePendingChange(long pendingId, boolean tryAgain) {
		this.pendingId = pendingId;
		this.tryAgain = tryAgain;
	}

	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		processor.resolvePendingChange(pendingId, tryAgain);
		this.setProcessed();
	}

	@Override
	public boolean isProcessed() {
		return this.isProcessed;
	}

	@Override
	public void setProcessed() {
		this.isProcessed = true;
	}
}
