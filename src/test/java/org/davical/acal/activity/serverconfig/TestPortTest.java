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


package org.davical.acal.activity.serverconfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.davical.acal.service.connector.AcalConnectionPool;
import org.davical.acal.service.connector.AcalRequestor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.core.app.ApplicationProvider;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

/**
 * Checking a server during setup, against servers on this machine.
 */
@RunWith(RobolectricTestRunner.class)
public class TestPortTest {

	private static final String HOST = "127.0.0.1";
	private static final String UNAUTHENTICATED =
			"<?xml version=\"1.0\"?><multistatus xmlns=\"DAV:\"><response><href>/</href><propstat><prop>"
			+ "<current-user-principal><unauthenticated/></current-user-principal>"
			+ "</prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>";

	private MockWebServer server;
	private MockWebServer otherServer;

	@Before
	public void setUp() throws IOException {
		AcalConnectionPool.initialize(ApplicationProvider.getApplicationContext());
		server = new MockWebServer();
		server.start(InetAddress.getByName(HOST), 0);
		otherServer = new MockWebServer();
		otherServer.start(InetAddress.getByName(HOST), 0);
	}

	@After
	public void tearDown() {
		// The timeouts belong to a client that every AcalRequestor shares
		AcalConnectionPool.setTimeOuts(60000, 30000);
		server.close();
		otherServer.close();
	}

	/**
	 * Setup sends the password without waiting to be asked, and guesses at
	 * how when a server says we are not logged in.  A server that ours has
	 * redirected us to must not be able to collect the password that way,
	 * and must not be able to keep us guessing for ever.
	 */
	@Test
	public void doesNotOfferThePasswordToAServerItWasRedirectedTo() throws Exception {
		server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				if ( "OPTIONS".equals(request.getMethod()) ) return new MockResponse.Builder().code(200).build();
				return new MockResponse.Builder().code(302)
						.addHeader("Location", "http://" + HOST + ":" + otherServer.getPort() + "/").build();
			}
		});
		otherServer.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				return new MockResponse.Builder().code(207)
						.addHeader("Content-Type", "text/xml; charset=utf-8").body(UNAUTHENTICATED).build();
			}
		});

		AcalRequestor requestor = new AcalRequestor(HOST, 0, server.getPort(), "/", "user1", "secret");
		assertFalse(new TestPort(requestor).hasCalDAV());

		assertTrue("The other server should have been asked", otherServer.getRequestCount() > 0);
		for( int i = otherServer.getRequestCount(); i > 0; i-- ) {
			RecordedRequest received = otherServer.takeRequest(5, TimeUnit.SECONDS);
			assertNotNull(received);
			assertNull("Sent to a server that never asked: " + received.getHeaders().get("Authorization"),
					received.getHeaders().get("Authorization"));
		}
	}
}
