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


package org.davical.acal.service.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.core.app.ApplicationProvider;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

/**
 * Finding a server's time zone service (RFC 7808) from its well-known URL.
 */
@RunWith(RobolectricTestRunner.class)
public class TimezoneServiceDiscoveryTest {

	private static final String CAPABILITIES =
			"{\"version\":1,\"info\":{\"primary-source\":\"IANA:2026a\"},"
			+ "\"actions\":[{\"name\":\"capabilities\",\"uri-template\":\"/capabilities\"},"
			+ "{\"name\":\"list\",\"uri-template\":\"/zones{?changedsince}\"}]}";

	private MockWebServer server;
	private String origin;

	@Before
	public void setUp() throws IOException {
		AcalConnectionPool.initialize(ApplicationProvider.getApplicationContext());
		server = new MockWebServer();
		server.start(InetAddress.getByName("127.0.0.1"), 0);
		origin = "http://127.0.0.1:" + server.getPort();
	}

	@After
	public void tearDown() {
		server.close();
	}

	private static MockResponse redirect(String location) {
		return new MockResponse.Builder().code(301).addHeader("Location", location).build();
	}

	private static MockResponse json(String body) {
		return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
	}

	@Test
	public void followsTheWellKnownUrlToTheService() throws Exception {
		server.enqueue(redirect("/tzdist/"));
		server.enqueue(redirect("/tzdist/capabilities"));
		server.enqueue(json(CAPABILITIES));

		assertEquals(origin + "/tzdist", TimezoneServiceDiscovery.discover(origin));
		assertEquals("/.well-known/timezone", server.takeRequest().getTarget());
		assertEquals("/tzdist/", server.takeRequest().getTarget());
		assertEquals("/tzdist/capabilities", server.takeRequest().getTarget());
	}

	@Test
	public void aServiceThatAnswersAtItsRootIsFoundThere() {
		server.enqueue(redirect("/tz/"));
		server.enqueue(json(CAPABILITIES));
		assertEquals(origin + "/tz", TimezoneServiceDiscovery.discover(origin));
	}

	@Test
	public void aServerWithNoServiceGivesNothing() {
		server.enqueue(new MockResponse.Builder().code(404).body("Not found").build());
		assertNull(TimezoneServiceDiscovery.discover(origin));
	}

	@Test
	public void jsonThatIsNotFromATimezoneServiceGivesNothing() {
		server.enqueue(json("{\"hello\":\"world\"}"));
		assertNull(TimezoneServiceDiscovery.discover(origin));
	}

	@Test
	public void somethingThatIsNotJsonGivesNothing() {
		server.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "text/html")
				.body("<html><body>Welcome</body></html>").build());
		assertNull(TimezoneServiceDiscovery.discover(origin));
	}

	@Test
	public void aServerThatIsNotThereGivesNothing() {
		server.close();
		assertNull(TimezoneServiceDiscovery.discover(origin));
	}

	@Test
	public void noServerGivesNothing() {
		assertNull(TimezoneServiceDiscovery.discover(null));
	}
}
