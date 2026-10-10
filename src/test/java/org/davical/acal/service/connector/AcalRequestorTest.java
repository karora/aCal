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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.davical.acal.providers.Servers;
import org.davical.acal.xml.DavNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.core.app.ApplicationProvider;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

/**
 * The HTTP client, talking to a server on this machine that gives the
 * responses each test lines up for it.
 */
@RunWith(RobolectricTestRunner.class)
public class AcalRequestorTest {

	private static final String HOST = "127.0.0.1";
	private static final int HTTP = 0;	// what Servers.USE_SSL holds for plain http
	private static final int HTTPS = 1;
	private static final String PROPFIND_BODY = "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><getetag/></prop></propfind>";
	private static final String MULTISTATUS =
			"<?xml version=\"1.0\"?><multistatus xmlns=\"DAV:\"><response><href>/cal/a.ics</href></response></multistatus>";

	private MockWebServer server;
	private MockWebServer otherServer;
	private AcalRequestor requestor;

	@Before
	public void setUp() throws IOException {
		AcalConnectionPool.initialize(ApplicationProvider.getApplicationContext());
		server = new MockWebServer();
		server.start(InetAddress.getByName(HOST), 0);
		otherServer = new MockWebServer();
		otherServer.start(InetAddress.getByName(HOST), 0);
		requestor = new AcalRequestor(HOST, HTTP, server.getPort(), "/cal/", "user1", "secret");
	}

	@After
	public void tearDown() {
		// The timeouts belong to a client that every AcalRequestor shares
		AcalConnectionPool.setTimeOuts(60000, 30000);
		server.close();
		otherServer.close();
	}

	private static MockResponse.Builder response(int code) {
		return new MockResponse.Builder().code(code);
	}

	private static MockResponse ok(String body) {
		return response(200).body(body).build();
	}

	private static MockResponse multistatus() {
		return response(207).addHeader("Content-Type", "text/xml; charset=utf-8").body(MULTISTATUS).build();
	}

	private static MockResponse redirect(int code, String location) {
		return response(code).addHeader("Location", location).build();
	}

	private static String urlOn(MockWebServer on, String path) {
		return "http://" + HOST + ":" + on.getPort() + path;
	}

	/** An https server with a certificate of its own making, which we are told to accept */
	private static MockWebServer secureServer() throws IOException {
		HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(HOST).build();
		HandshakeCertificates certificates = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
		MockWebServer secureServer = new MockWebServer();
		secureServer.useHttps(certificates.sslSocketFactory());
		secureServer.start(InetAddress.getByName(HOST), 0);
		EasyX509TrustManager.unpinHost(ApplicationProvider.getApplicationContext(), HOST, secureServer.getPort());
		return secureServer;
	}

	private static RecordedRequest next(MockWebServer from) throws InterruptedException {
		RecordedRequest request = from.takeRequest(5, TimeUnit.SECONDS);
		assertNotNull("The server was not sent the request that was expected", request);
		return request;
	}

	private static String read(InputStream in) throws IOException {
		if ( in == null ) return null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		for( int count; (count = in.read(buffer)) > 0; ) out.write(buffer, 0, count);
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private static String md5(String text) throws Exception {
		StringBuilder hex = new StringBuilder();
		for( byte b : MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)) ) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	private static String parameter(String header, String name) {
		Matcher m = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(header);
		assertTrue("No " + name + " in: " + header, m.find());
		return m.group(1);
	}

	// ---- what is sent

	@Test
	public void sendsTheMethodPathHeadersAndBody() throws Exception {
		server.enqueue(multistatus());
		Header[] headers = { new BasicHeader("Depth", "1"), new BasicHeader("Content-Type", "text/xml; charset=UTF-8") };
		String body = read(requestor.doRequest("PROPFIND", "/cal/home/", headers, PROPFIND_BODY));

		assertEquals(207, requestor.getStatusCode());
		assertEquals(MULTISTATUS, body);

		RecordedRequest sent = next(server);
		assertEquals("PROPFIND", sent.getMethod());
		assertEquals("/cal/home/", sent.getTarget());
		assertEquals("1", sent.getHeaders().get("Depth"));
		assertNotNull(sent.getHeaders().get("User-Agent"));
		assertNull("No credentials should be sent before they are asked for", sent.getHeaders().get("Authorization"));
		assertEquals(PROPFIND_BODY, sent.getBody().utf8());
	}

	@Test
	public void aNullPathUsesThePathItHasAlready() throws Exception {
		server.enqueue(ok("hello"));
		requestor.doRequest("GET", null, null, null);
		assertEquals("/cal/", next(server).getTarget());
	}

	@Test
	public void keepsTheQueryStringOfAPath() throws Exception {
		server.enqueue(ok("hello"));
		requestor.doRequest("GET", "/tz/zones?changedsince=2026-01-01T00:00:00Z", null, null);
		assertEquals("/tz/zones?changedsince=2026-01-01T00:00:00Z", next(server).getTarget());
	}

	@Test
	public void givesTheResponseHeaders() throws Exception {
		server.enqueue(response(200).addHeader("ETag", "\"abc\"").addHeader("DAV", "1, 3, calendar-access").body("x").build());
		requestor.doRequest("GET", "/cal/a.ics", null, null);

		String etag = null;
		for( Header header : requestor.getResponseHeaders() ) {
			if ( header.getName().equalsIgnoreCase("ETag") ) etag = header.getValue();
		}
		assertEquals("\"abc\"", etag);
	}

	// ---- authentication

	@Test
	public void answersARequestForBasicAuthentication() throws Exception {
		server.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"DAViCal CalDAV Server\"").build());
		server.enqueue(multistatus());
		String body = read(requestor.doRequest("PROPFIND", "/cal/", null, PROPFIND_BODY));

		assertEquals(207, requestor.getStatusCode());
		assertEquals(MULTISTATUS, body);
		assertEquals(Servers.AUTH_BASIC, requestor.getAuthType());

		assertNull(next(server).getHeaders().get("Authorization"));
		RecordedRequest second = next(server);
		assertEquals("Basic " + Base64.getEncoder().encodeToString("user1:secret".getBytes(StandardCharsets.UTF_8)),
				second.getHeaders().get("Authorization"));
		assertEquals("PROPFIND", second.getMethod());
		assertEquals(PROPFIND_BODY, second.getBody().utf8());
	}

	@Test
	public void goesOnSendingCredentialsOnceTheyHaveBeenAskedFor() throws Exception {
		server.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"x\"").build());
		server.enqueue(ok("one"));
		server.enqueue(ok("two"));
		requestor.doRequest("GET", "/cal/a.ics", null, null);
		requestor.doRequest("GET", "/cal/b.ics", null, null);

		assertEquals(3, server.getRequestCount());
		next(server);
		next(server);
		assertNotNull(next(server).getHeaders().get("Authorization"));
	}

	@Test
	public void answersARequestForDigestAuthentication() throws Exception {
		server.enqueue(response(401).addHeader("WWW-Authenticate",
				"Digest realm=\"DAViCal CalDAV Server\", qop=\"auth\", nonce=\"55a1a0c53c0f337e\", opaque=\"52295deb26cc99c2\", algorithm=\"MD5\"").build());
		server.enqueue(multistatus());
		requestor.doRequest("PROPFIND", "/cal/home/", null, PROPFIND_BODY);

		assertEquals(207, requestor.getStatusCode());
		assertEquals(Servers.AUTH_DIGEST, requestor.getAuthType());
		next(server);
		String authorization = next(server).getHeaders().get("Authorization");
		assertTrue(authorization, authorization.startsWith("Digest "));
		assertEquals("user1", parameter(authorization, "username"));
		assertEquals("DAViCal CalDAV Server", parameter(authorization, "realm"));
		assertEquals("55a1a0c53c0f337e", parameter(authorization, "nonce"));
		assertEquals("52295deb26cc99c2", parameter(authorization, "opaque"));
		assertEquals("/cal/home/", parameter(authorization, "uri"));
		assertEquals("auth", parameter(authorization, "qop"));

		// RFC 2617 section 3.2.2
		String ha1 = md5("user1:DAViCal CalDAV Server:secret");
		String ha2 = md5("PROPFIND:/cal/home/");
		String expected = md5(ha1 + ":55a1a0c53c0f337e:" + parameter(authorization, "nc") + ":"
				+ parameter(authorization, "cnonce") + ":auth:" + ha2);
		assertEquals(expected, parameter(authorization, "response"));
	}

	@Test
	public void prefersDigestWhenTheServerOffersBoth() throws Exception {
		server.enqueue(response(401)
				.addHeader("WWW-Authenticate", "Basic realm=\"x\"")
				.addHeader("WWW-Authenticate", "Digest realm=\"x\", qop=\"auth\", nonce=\"abc\", opaque=\"def\"").build());
		server.enqueue(ok("hello"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertEquals(Servers.AUTH_DIGEST, requestor.getAuthType());
	}

	@Test
	public void givesUpWhenTheCredentialsAreRefused() throws Exception {
		server.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"x\"").build());
		server.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"x\"").build());
		requestor.doRequest("GET", "/cal/", null, null);

		assertEquals(401, requestor.getStatusCode());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void canSendCredentialsWithoutBeingAsked() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(ok("hello"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertNotNull(next(server).getHeaders().get("Authorization"));
		assertEquals(1, server.getRequestCount());
	}

	// ---- redirects

	@Test
	public void followsARedirectToAPathKeepingTheMethodAndBody() throws Exception {
		server.enqueue(redirect(301, "/caldav.php/user1/"));
		server.enqueue(multistatus());
		String body = read(requestor.doRequest("PROPFIND", "/.well-known/caldav", null, PROPFIND_BODY));

		assertEquals(207, requestor.getStatusCode());
		assertEquals(MULTISTATUS, body);

		assertEquals("/.well-known/caldav", next(server).getTarget());
		RecordedRequest second = next(server);
		assertEquals("PROPFIND", second.getMethod());
		assertEquals("/caldav.php/user1/", second.getTarget());
		assertEquals(PROPFIND_BODY, second.getBody().utf8());
	}

	@Test
	public void followsARedirectToAnotherServer() throws Exception {
		server.enqueue(redirect(302, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(ok("from the other server"));
		String body = read(requestor.doRequest("GET", "/cal/", null, null));

		assertEquals("from the other server", body);
		assertTrue(requestor.wasRedirected());
		assertEquals("/elsewhere/", next(otherServer).getTarget());
	}

	/**
	 * A redirect is an answer to one request.  The next request goes to the
	 * server we were set up with, not to wherever that one was sent.
	 */
	@Test
	public void aRedirectOnlyAppliesToTheRequestThatWasRedirected() throws Exception {
		server.enqueue(redirect(301, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(ok("from the other server"));
		server.enqueue(ok("from our server"));

		requestor.doRequest("GET", "/cal/", null, null);
		assertEquals(urlOn(server, "/cal/"), requestor.fullUrl());

		assertEquals("from our server", read(requestor.doRequest("GET", null, null, null)));
		assertEquals(1, otherServer.getRequestCount());
		next(server);
		assertEquals("/cal/", next(server).getTarget());
	}

	@Test
	public void aRedirectToAnotherPathOnlyAppliesToTheRequestThatWasRedirected() throws Exception {
		server.enqueue(redirect(301, "/moved/"));
		server.enqueue(ok("moved"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertEquals("/cal/", requestor.getPath());
	}

	@Test
	public void discoveryCanAdoptWhereARedirectLeads() throws Exception {
		requestor.adoptRedirects();
		server.enqueue(redirect(301, urlOn(otherServer, "/caldav.php/")));
		otherServer.enqueue(ok("one"));
		otherServer.enqueue(ok("two"));

		requestor.doRequest("GET", "/.well-known/caldav", null, null);
		assertEquals(urlOn(otherServer, "/caldav.php/"), requestor.fullUrl());
		assertEquals(otherServer.getPort(), requestor.getPort());

		requestor.doRequest("GET", "/caldav.php/user1/", null, null);
		assertEquals(2, otherServer.getRequestCount());
	}

	/**
	 * Our credentials are for the server we were set up with.  A redirect
	 * must not hand them to whoever it names.
	 */
	@Test
	public void doesNotSendCredentialsToAnotherServerItIsRedirectedTo() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(redirect(302, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(ok("from the other server"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertNotNull(next(server).getHeaders().get("Authorization"));
		assertNull(next(otherServer).getHeaders().get("Authorization"));
	}

	@Test
	public void doesNotSendCredentialsToAnotherServerEvenWhenAdoptingRedirects() throws Exception {
		requestor.adoptRedirects();
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(redirect(302, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(ok("one"));
		otherServer.enqueue(ok("two"));
		requestor.doRequest("GET", "/cal/", null, null);
		assertEquals(Servers.AUTH_NONE, requestor.getAuthType());

		// Server setup does this before each request, and guesses at the type
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		requestor.doRequest("GET", "/elsewhere/more/", null, null);

		assertNull(next(otherServer).getHeaders().get("Authorization"));
		assertNull(next(otherServer).getHeaders().get("Authorization"));
	}

	@Test
	public void sendsCredentialsToAnAdoptedServerOnceItHasAskedForThem() throws Exception {
		requestor.adoptRedirects();
		server.enqueue(redirect(302, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"other\"").build());
		otherServer.enqueue(ok("one"));
		otherServer.enqueue(ok("two"));
		requestor.doRequest("GET", "/cal/", null, null);
		requestor.doRequest("GET", "/elsewhere/more/", null, null);

		assertNull(next(otherServer).getHeaders().get("Authorization"));
		assertNotNull(next(otherServer).getHeaders().get("Authorization"));
		assertNotNull(next(otherServer).getHeaders().get("Authorization"));
	}

	/**
	 * Asking is not enough.  Whoever can redirect us could ask, so outside
	 * of setting up a server the answer is no.
	 */
	@Test
	public void doesNotAnswerAnotherServerThatAsksForCredentials() throws Exception {
		server.enqueue(redirect(301, urlOn(otherServer, "/caldav.php/")));
		otherServer.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"other\"").build());
		otherServer.enqueue(multistatus());
		requestor.doRequest("PROPFIND", "/cal/", null, PROPFIND_BODY);

		assertEquals(401, requestor.getStatusCode());
		assertEquals(1, otherServer.getRequestCount());
		assertNull(next(otherServer).getHeaders().get("Authorization"));
		assertEquals(urlOn(server, "/cal/"), requestor.fullUrl());
	}

	@Test
	public void discoveryAnswersAnotherServerThatAsksForCredentialsItself() throws Exception {
		requestor.adoptRedirects();
		server.enqueue(redirect(301, urlOn(otherServer, "/caldav.php/")));
		otherServer.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"other\"").build());
		otherServer.enqueue(multistatus());
		requestor.doRequest("PROPFIND", "/.well-known/caldav", null, PROPFIND_BODY);

		assertEquals(207, requestor.getStatusCode());
		assertNull(next(otherServer).getHeaders().get("Authorization"));
		assertNotNull(next(otherServer).getHeaders().get("Authorization"));
	}

	/**
	 * What another server asked of us is forgotten along with the redirect,
	 * and what our own server asked of us is remembered.
	 */
	@Test
	public void goesBackToAuthenticatingWithItsOwnServerAfterARedirectElsewhere() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(redirect(302, urlOn(otherServer, "/elsewhere/")));
		otherServer.enqueue(response(401).addHeader("WWW-Authenticate",
				"Digest realm=\"other\", qop=\"auth\", nonce=\"abc\", opaque=\"def\"").build());
		server.enqueue(ok("from our server"));

		requestor.doRequest("GET", "/cal/", null, null);
		assertEquals(Servers.AUTH_BASIC, requestor.getAuthType());

		requestor.doRequest("GET", "/cal/", null, null);
		next(server);
		String authorization = next(server).getHeaders().get("Authorization");
		assertNotNull(authorization);
		assertTrue(authorization, authorization.startsWith("Basic "));
	}

	/**
	 * Which server is ours is worked out afresh at every step, so it does
	 * not matter how many steps a redirect takes to get somewhere else.
	 */
	@Test
	public void aLongerWayRoundToAnotherServerGetsItNoCredentials() throws Exception {
		MockWebServer secureOther = secureServer();
		try {
			requestor.setAuthType(Servers.AUTH_BASIC);
			requestor.setAuthRequired();
			server.enqueue(redirect(302, urlOn(otherServer, "/one/")));
			otherServer.enqueue(redirect(302, "/two/"));
			// The other server moving to https does not make it ours
			otherServer.enqueue(redirect(308, "https://" + HOST + ":" + secureOther.getPort() + "/three/"));
			secureOther.enqueue(response(401).addHeader("WWW-Authenticate", "Basic realm=\"other\"").build());
			secureOther.enqueue(ok("too late"));
			requestor.doRequest("GET", "/cal/", null, null);

			assertEquals(401, requestor.getStatusCode());
			assertNull(next(otherServer).getHeaders().get("Authorization"));
			assertNull(next(otherServer).getHeaders().get("Authorization"));
			assertEquals(1, secureOther.getRequestCount());
			assertNull(next(secureOther).getHeaders().get("Authorization"));
		}
		finally {
			secureOther.close();
		}
	}

	@Test
	public void aRedirectBackToItsOwnServerGetsItsCredentialsAgain() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(redirect(302, urlOn(otherServer, "/login-check/")));
		otherServer.enqueue(redirect(302, urlOn(server, "/cal/home/")));
		server.enqueue(ok("home"));

		assertEquals("home", read(requestor.doRequest("GET", "/cal/", null, null)));
		assertNull(next(otherServer).getHeaders().get("Authorization"));
		next(server);
		assertNotNull(next(server).getHeaders().get("Authorization"));
	}

	@Test
	public void anAdoptedServerMovingToHttpsStillHasToAsk() throws Exception {
		MockWebServer secureOther = secureServer();
		try {
			requestor.adoptRedirects();
			server.enqueue(redirect(302, urlOn(otherServer, "/one/")));
			otherServer.enqueue(ok("one"));
			requestor.doRequest("GET", "/cal/", null, null);

			requestor.setAuthType(Servers.AUTH_BASIC);
			requestor.setAuthRequired();
			otherServer.enqueue(redirect(308, "https://" + HOST + ":" + secureOther.getPort() + "/two/"));
			secureOther.enqueue(ok("two"));
			secureOther.enqueue(ok("three"));
			requestor.doRequest("GET", "/one/", null, null);
			requestor.setAuthRequired();
			requestor.doRequest("GET", "/two/", null, null);

			next(otherServer);
			assertNull(next(otherServer).getHeaders().get("Authorization"));
			assertNull(next(secureOther).getHeaders().get("Authorization"));
			assertNull(next(secureOther).getHeaders().get("Authorization"));
		}
		finally {
			secureOther.close();
		}
	}

	@Test
	public void keepsSendingCredentialsAfterARedirectOnTheSameServer() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		server.enqueue(redirect(301, "/caldav.php/user1/"));
		server.enqueue(ok("hello"));
		requestor.doRequest("GET", "/cal/", null, null);

		next(server);
		assertNotNull(next(server).getHeaders().get("Authorization"));
	}

	@Test
	public void aDifferentPortIsADifferentServer() throws Exception {
		requestor.setAuthType(Servers.AUTH_BASIC);
		requestor.setAuthRequired();
		// The same address as our server, so only the port tells them apart
		server.enqueue(redirect(302, urlOn(otherServer, "/cal/")));
		otherServer.enqueue(ok("hello"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertNull(next(otherServer).getHeaders().get("Authorization"));
	}

	@Test
	public void neverFollowsARedirectFromHttpsToHttp() throws Exception {
		MockWebServer secureServer = secureServer();
		try {
			AcalRequestor secure = new AcalRequestor(HOST, HTTPS, secureServer.getPort(), "/cal/", "user1", "secret");
			secure.adoptRedirects();
			secure.setAuthType(Servers.AUTH_BASIC);
			secure.setAuthRequired();
			secureServer.enqueue(redirect(301, urlOn(otherServer, "/cal/")));
			secure.doRequest("GET", "/cal/", null, null);

			assertEquals("The https server should have been reached", 1, secureServer.getRequestCount());
			assertEquals(301, secure.getStatusCode());
			assertEquals(0, otherServer.getRequestCount());
			assertEquals("https", secure.getProtocol());
			assertEquals("https://" + HOST + ":" + secureServer.getPort() + "/cal/", secure.fullUrl());
			assertTrue(!secure.wasRedirected());
		}
		finally {
			secureServer.close();
		}
	}

	@Test
	public void followsARedirectFromHttpToHttps() throws Exception {
		MockWebServer secureServer = secureServer();
		try {
			server.enqueue(redirect(308, "https://" + HOST + ":" + secureServer.getPort() + "/cal/"));
			secureServer.enqueue(ok("securely"));

			assertEquals("securely", read(requestor.doRequest("GET", "/cal/", null, null)));
		}
		finally {
			secureServer.close();
		}
	}

	@Test
	public void onlyTheSamePlaceOrItsHttpsCounterpartIsTheSameServer() {
		assertTrue(AcalRequestor.isSameServer("https", "dav.example.net", 443, "https", "dav.example.net", 443));
		assertTrue(AcalRequestor.isSameServer("https", "dav.example.net", 443, "https", "DAV.example.net", 443));
		assertTrue(AcalRequestor.isSameServer("http", "dav.example.net", 80, "https", "dav.example.net", 443));

		assertTrue(!AcalRequestor.isSameServer("https", "dav.example.net", 443, "https", "example.net", 443));
		assertTrue(!AcalRequestor.isSameServer("https", "dav.example.net", 443, "https", "dav.example.net.evil.example", 443));
		assertTrue(!AcalRequestor.isSameServer("https", "dav.example.net", 443, "https", "dav.example.net", 8443));
		assertTrue(!AcalRequestor.isSameServer("https", "dav.example.net", 443, "http", "dav.example.net", 80));
		// Someone else may be running whatever answers on another port
		assertTrue(!AcalRequestor.isSameServer("http", "dav.example.net", 80, "https", "dav.example.net", 8443));
		assertTrue(!AcalRequestor.isSameServer("http", "dav.example.net", 8008, "https", "dav.example.net", 443));
		assertTrue(!AcalRequestor.isSameServer(null, null, 0, "https", "dav.example.net", 443));
	}

	@Test
	public void followsEachKindOfRedirect() throws Exception {
		for( int code : new int[] { 301, 302, 303, 307, 308 } ) {
			server.enqueue(redirect(code, "/moved/" + code));
			server.enqueue(ok("moved " + code));
			assertEquals("After a " + code, "moved " + code, read(requestor.doRequest("GET", "/cal/", null, null)));
		}
	}

	@Test
	public void givesUpOnARedirectThatNeverEnds() throws Exception {
		for( int i = 0; i < 20; i++ ) server.enqueue(redirect(302, "/round-and-round"));
		requestor.doRequest("GET", "/cal/", null, null);

		assertEquals(302, requestor.getStatusCode());
		assertTrue("Sent " + server.getRequestCount() + " requests", server.getRequestCount() <= 6);
	}

	/**
	 * The limit is on the redirects for one request.  It used to be on all
	 * the redirects an AcalRequestor ever followed, so one that was kept in
	 * use stopped following them.
	 */
	@Test
	public void goesOnFollowingRedirectsForLaterRequests() throws Exception {
		for( int i = 1; i <= 8; i++ ) {
			server.enqueue(redirect(301, "/moved/" + i));
			server.enqueue(ok("request " + i));
			assertEquals("request " + i, read(requestor.doRequest("GET", "/cal/" + i, null, null)));
		}
	}

	// ---- when things go wrong

	@Test
	public void reportsNotFound() throws Exception {
		server.enqueue(response(404).body("No such resource").build());
		assertEquals("No such resource", read(requestor.doRequest("GET", "/cal/missing.ics", null, null)));
		assertEquals(404, requestor.getStatusCode());
	}

	@Test
	public void reportsAServerError() throws Exception {
		server.enqueue(response(500).body("Oops").build());
		requestor.doRequest("GET", "/cal/", null, null);
		assertEquals(500, requestor.getStatusCode());
	}

	@Test
	public void reportsAPreconditionFailure() throws Exception {
		server.enqueue(response(412).build());
		Header[] headers = { new BasicHeader("If-Match", "\"old-etag\"") };
		requestor.doRequest("PUT", "/cal/a.ics", headers, "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

		assertEquals(412, requestor.getStatusCode());
		RecordedRequest sent = next(server);
		assertEquals("PUT", sent.getMethod());
		assertEquals("\"old-etag\"", sent.getHeaders().get("If-Match"));
	}

	@Test
	public void aServerThatIsNotThereGivesNoResponse() throws Exception {
		int port = server.getPort();
		server.close();
		assertNull(requestor.doRequest("GET", "/cal/", null, null));
		assertEquals(-1, requestor.getStatusCode());
		assertEquals(port, requestor.getPort());
	}

	@Test
	public void aServerThatIsTooSlowGivesNoResponse() throws Exception {
		requestor.setTimeOuts(2000, 300);
		server.enqueue(response(200).headersDelay(3, TimeUnit.SECONDS).body("too late").build());

		long started = System.currentTimeMillis();
		assertNull(requestor.doRequest("GET", "/cal/", null, null));
		assertEquals(-1, requestor.getStatusCode());
		assertTrue("Should have given up well before the server answered", System.currentTimeMillis() - started < 2500);
	}

	// ---- XML responses

	@Test
	public void parsesAMultistatusResponse() throws Exception {
		server.enqueue(multistatus());
		DavNode root = requestor.doXmlRequest("PROPFIND", "/cal/", null, PROPFIND_BODY);

		assertNotNull(root);
		assertEquals("/cal/a.ics", root.getFirstNodeText("multistatus/response/href"));
	}

	@Test
	public void aResponseThatDoesNotSayItIsXmlIsNotParsed() throws Exception {
		server.enqueue(response(200).addHeader("Content-Type", "text/html").body("<html><body>Login</body></html>").build());
		assertNull(requestor.doXmlRequest("PROPFIND", "/cal/", null, PROPFIND_BODY));
	}

	@Test
	public void notFoundIsNotParsedEvenIfItIsXml() throws Exception {
		server.enqueue(response(404).addHeader("Content-Type", "text/xml").body(MULTISTATUS).build());
		assertNull(requestor.doXmlRequest("PROPFIND", "/cal/", null, PROPFIND_BODY));
		assertEquals(404, requestor.getStatusCode());
	}

	@Test
	public void anEmptyXmlResponseGivesNothing() throws Exception {
		server.enqueue(response(207).addHeader("Content-Type", "text/xml").build());
		assertNull(requestor.doXmlRequest("PROPFIND", "/cal/", null, PROPFIND_BODY));
	}

	// ---- URLs

	@Test
	public void understandsAFullUrl() {
		AcalRequestor r = new AcalRequestor();
		r.interpretUriString("https://dav.example.net:8443/caldav.php/user1/");
		assertEquals("dav.example.net", r.getHostName());
		assertEquals(8443, r.getPort());
		assertEquals("https", r.getProtocol());
		assertEquals("/caldav.php/user1/", r.getPath());
		assertEquals("https://dav.example.net:8443/caldav.php/user1/", r.fullUrl());
	}

	@Test
	public void leavesOutTheUsualPortForTheProtocol() {
		AcalRequestor r = new AcalRequestor();
		r.interpretUriString("https://dav.example.net/cal/");
		assertEquals(443, r.getPort());
		assertEquals("https://dav.example.net/cal/", r.fullUrl());

		r.interpretUriString("http://dav.example.net/cal/");
		assertEquals(80, r.getPort());
		assertEquals("http://dav.example.net/cal/", r.fullUrl());
	}

	@Test
	public void aPathOnItsOwnKeepsTheServer() {
		AcalRequestor r = new AcalRequestor();
		r.interpretUriString("https://dav.example.net:8443/first/");
		r.interpretUriString("/second/");
		assertEquals("https://dav.example.net:8443/second/", r.fullUrl());
	}

	@Test
	public void understandsAnIpv6Address() {
		AcalRequestor r = new AcalRequestor();
		r.interpretUriString("http://[2001:db8::1]:8008/cal/");
		assertEquals("[2001:db8::1]", r.getHostName());
		assertEquals(8008, r.getPort());
		assertEquals("/cal/", r.getPath());
	}

	@Test
	public void understandsAHostNameWithNoDots() {
		AcalRequestor r = new AcalRequestor();
		r.interpretUriString("http://localhost:8008/cal/");
		assertEquals("localhost", r.getHostName());
		assertEquals(8008, r.getPort());
		assertEquals("/cal/", r.getPath());
	}
}
