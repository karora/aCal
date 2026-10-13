package org.davical.acal.activity.serverconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.database.TestDatabase;
import org.davical.acal.providers.Servers;
import org.davical.acal.service.FakeCalDavServer;
import org.davical.acal.service.connector.AcalRequestor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Working out, from what the user typed when adding a server, where on it
 * their calendars are to be found.
 */
@RunWith(AndroidJUnit4.class)
public class TestPortTest {

	private FakeCalDavServer server;

	@Before
	public void setUp() throws Exception {
		TestDatabase.startWithNothing();
		server = new FakeCalDavServer();
		server.start(TestDatabase.context());
	}

	@After
	public void tearDown() {
		server.stop();
	}

	private TestPort probing( String path, String password ) {
		return new TestPort(new AcalRequestor(FakeCalDavServer.HOST, 1, server.port(), path,
				FakeCalDavServer.USERNAME, password));
	}

	private void assertFoundThePrincipal( TestPort probe ) {
		assertEquals(TestPort.HAS_CALDAV, probe.getAchievement());
		ContentValues settings = new ContentValues();
		probe.applyToServerSettings(settings);
		assertEquals(FakeCalDavServer.HOST, settings.getAsString(Servers.HOSTNAME));
		assertEquals(server.port(), (int) settings.getAsInteger(Servers.PORT));
		assertEquals(1, (int) settings.getAsInteger(Servers.USE_SSL));
		assertEquals(FakeCalDavServer.PRINCIPAL, settings.getAsString(Servers.PRINCIPAL_PATH));
		assertEquals(Servers.AUTH_BASIC, (int) settings.getAsInteger(Servers.AUTH_TYPE));
	}

	@Test
	public void givenThePrincipalItFindsCalDavThere() {
		TestPort probe = probing(FakeCalDavServer.PRINCIPAL, FakeCalDavServer.PASSWORD);

		assertTrue(probe.hasCalDAV());

		assertFoundThePrincipal(probe);
	}

	@Test
	public void givenAnyDavAddressItIsToldWhoTheUserIs() {
		TestPort probe = probing(FakeCalDavServer.CALENDAR, FakeCalDavServer.PASSWORD);

		assertTrue(probe.hasCalDAV());

		assertFoundThePrincipal(probe);
	}

	@Test
	public void givenOnlyTheServerItFollowsTheWellKnownAddress() {
		TestPort probe = probing("/", FakeCalDavServer.PASSWORD);

		assertTrue(probe.hasCalDAV());

		assertFoundThePrincipal(probe);
		boolean askedTheWellKnownAddress = false;
		for( FakeCalDavServer.Asked each : server.asked("PROPFIND") ) {
			if ( each.path.equals(FakeCalDavServer.DAV_ROOT) ) askedTheWellKnownAddress = true;
		}
		assertTrue("It was sent on to where DAV starts", askedTheWellKnownAddress);
	}

	@Test
	public void theWrongPasswordIsReportedAsThat() {
		TestPort probe = probing(FakeCalDavServer.PRINCIPAL, "not the password");

		assertFalse(probe.hasCalDAV());

		assertFalse(probe.authOK());
		assertEquals(TestPort.AUTH_FAILED, probe.getAchievement());
	}

	@Test
	public void aServerThatIsNotDavIsReportedAsThat() {
		server.isNotADavServer();
		TestPort probe = probing("/", FakeCalDavServer.PASSWORD);

		assertFalse(probe.hasCalDAV());

		assertEquals(TestPort.NO_DAV_RESPONSE, probe.getAchievement());
	}
}
