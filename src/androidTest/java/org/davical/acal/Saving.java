package org.davical.acal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;

import org.davical.acal.activity.RRResourceEditedRequest;
import org.davical.acal.database.resourcesmanager.ResourceManager;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.davacal.VComponent;

/**
 * What a screen does when the user saves or deletes something.
 */
public final class Saving {

	private Saving() { }

	/**
	 * Send the request a screen sends, and wait for the answer it gets.
	 *
	 * @param resourceId the resource changed or deleted, or -1 for a new one
	 * @param action one of the ACTION_ constants of RRResourceEditedRequest
	 * @return the id of the resource
	 */
	public static long send( Context context, long collectionId, long resourceId, String data, int action ) throws Exception {
		final CountDownLatch answered = new CountDownLatch(1);
		final AtomicReference<Long> answer = new AtomicReference<Long>();
		ResourceManager.getInstance(context).sendRequest(new RRResourceEditedRequest(
				new ResourceResponseListener<Long>() {
					@Override
					public void resourceResponse( ResourceResponse<Long> response ) {
						answer.set(response.result());
						answered.countDown();
					}
				}, collectionId, resourceId, VComponent.createComponentFromBlob(data), action));
		assertTrue("The save was not answered", answered.await(30, TimeUnit.SECONDS));
		assertNotNull("The save was refused", answer.get());
		return answer.get();
	}
}
