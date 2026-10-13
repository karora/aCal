package org.davical.acal.database.resourcesmanager.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import android.content.ContentValues;

import org.davical.acal.database.CacheWindow;
import org.davical.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import org.davical.acal.database.resourcesmanager.ResourceResponse;
import org.davical.acal.database.resourcesmanager.ResourceResponseListener;
import org.davical.acal.dataservice.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The cache fills the range of days it covers from what this request reads.
 * It has to have dealt with the answer before the request is finished,
 * because only until then is it certain that nothing read has since changed.
 */
@RunWith(RobolectricTestRunner.class)
public class RRGetCacheEventsInRangeTest {

	private static final String EVENT = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nSUMMARY:One\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

	/** A table with one event in it and nothing waiting to be sent. */
	private static ReadOnlyResourceTableManager tableWithOneEvent() {
		return (ReadOnlyResourceTableManager) Proxy.newProxyInstance(
				RRGetCacheEventsInRangeTest.class.getClassLoader(),
				new Class<?>[] { ReadOnlyResourceTableManager.class },
				new InvocationHandler() {
					@Override
					public Object invoke( Object proxy, Method method, Object[] args ) {
						ArrayList<ContentValues> rows = new ArrayList<ContentValues>();
						if ( method.getName().equals("query") ) {
							ContentValues row = new ContentValues();
							row.put(ResourceTableManager.RESOURCE_ID, 7L);
							row.put(ResourceTableManager.COLLECTION_ID, 3L);
							row.put(ResourceTableManager.RESOURCE_NAME, "one.ics");
							row.put(ResourceTableManager.RESOURCE_DATA, EVENT);
							row.put(ResourceTableManager.EFFECTIVE_TYPE, "VEVENT");
							rows.add(row);
						}
						return rows;
					}
				});
	}

	/**
	 * The answer went to another thread, which worked out what to show while
	 * the next write went ahead.  When that write deleted an event, the cache
	 * removed it and then stored it again from what had been read before.
	 */
	@Test
	public void theAnswerHasBeenDealtWithByTheTimeTheReadIsFinished() {
		final Thread reader = Thread.currentThread();
		final AtomicReference<Thread> answeredOn = new AtomicReference<Thread>();
		final AtomicReference<ArrayList<Resource>> answer = new AtomicReference<ArrayList<Resource>>();
		RRGetCacheEventsInRange request = new RRGetCacheEventsInRange(new CacheWindow(null, null),
				new ResourceResponseListener<ArrayList<Resource>>() {
					@Override
					public void resourceResponse( ResourceResponse<ArrayList<Resource>> response ) {
						answeredOn.set(Thread.currentThread());
						answer.set(response.result());
					}
				});

		request.process(tableWithOneEvent());

		assertSame("The answer must be dealt with by the thread doing the read, before it goes on",
				reader, answeredOn.get());
		assertEquals(1, answer.get().size());
		assertEquals(7L, answer.get().get(0).getResourceId());
		assertTrue(request.isProcessed());
	}
}
