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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import android.app.NotificationManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

/**
 * Whether a connection is allowed, for each combination of what the server
 * presents and what the user has decided before.
 *
 * "Trusted" here means a certificate the platform would accept, which is
 * arranged by giving the trust manager a key store that holds it.  The
 * certificates are in src/test/resources/certs and are valid until 2126.
 */
@RunWith(RobolectricTestRunner.class)
public class EasyX509TrustManagerTest {

	// The name the trusted certificate is for
	private static final String HOST = "trusted.test.invalid";
	private static final int PORT = 443;

	// As reported by: openssl x509 -noout -fingerprint -sha256
	private static final String SELF_SIGNED_FINGERPRINT =
			"f065d7acec53639e1a64d2cb57a94eb5daaef8a8b133d7c143414d66dbc6c12a";

	private Context context;
	private X509Certificate trusted;
	private X509Certificate selfSigned;
	private X509Certificate replacement;
	private EasyX509TrustManager trustManager;

	private static X509Certificate certificate(String name) throws Exception {
		try( InputStream in = EasyX509TrustManagerTest.class.getResourceAsStream("/certs/" + name + ".pem") ) {
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
		}
	}

	@Before
	public void setUp() throws Exception {
		context = ApplicationProvider.getApplicationContext();
		trusted = certificate("trusted");
		selfSigned = certificate("self-signed");
		replacement = certificate("self-signed-replacement");

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setCertificateEntry("trusted", trusted);
		trustManager = new EasyX509TrustManager(keyStore, context);
	}

	@After
	public void forgetPendingNotifications() {
		// These are remembered in a static field
		EasyX509TrustManager.clearPendingNotification(HOST, PORT);
		EasyX509TrustManager.clearPendingNotification(HOST, 8443);
	}

	/** What a connection to this port of the host is told when the server presents this certificate. */
	private boolean connect(X509Certificate presented, int port) throws Exception {
		return trustManager.verify(HOST, port, new X509Certificate[] { presented });
	}

	private boolean connect(X509Certificate presented) throws Exception {
		return connect(presented, PORT);
	}

	private int notificationsShowing() {
		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		return Shadows.shadowOf(manager).getAllNotifications().size();
	}

	private static String fingerprint(X509Certificate certificate) {
		return EasyX509TrustManager.getCertificateFingerprint(certificate);
	}

	@Test
	public void fingerprintIsTheSha256OfTheCertificate() {
		assertEquals(SELF_SIGNED_FINGERPRINT, fingerprint(selfSigned));
	}

	@Test
	public void fingerprintIsFormattedForReading() {
		assertEquals("F0:65:D7:AC:EC:53:63:9E\n1A:64:D2:CB:57:A9:4E:B5\nDA:AE:F8:A8:B1:33:D7:C1\n43:41:4D:66:DB:C6:C1:2A",
				EasyX509TrustManager.formatFingerprint(SELF_SIGNED_FINGERPRINT));
	}

	@Test
	public void trustedCertificateIsAccepted() throws Exception {
		assertTrue(connect(trusted));
		assertEquals(0, notificationsShowing());
	}

	@Test
	public void unknownSelfSignedCertificateIsRefusedAndTheUserAsked() throws Exception {
		assertFalse(connect(selfSigned));
		assertEquals(1, notificationsShowing());
	}

	@Test
	public void theUserIsOnlyAskedOnceWhileTheQuestionIsShowing() throws Exception {
		assertFalse(connect(selfSigned));
		assertFalse(connect(selfSigned));
		assertFalse(connect(selfSigned));
		assertEquals(1, notificationsShowing());
	}

	@Test
	public void acceptedSelfSignedCertificateIsAllowedFromThenOn() throws Exception {
		assertFalse(connect(selfSigned));
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));

		assertTrue(connect(selfSigned));
		assertTrue(connect(selfSigned));
	}

	@Test
	public void aDifferentCertificateFromTheOneAcceptedIsRefusedAndTheUserAsked() throws Exception {
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));

		assertFalse(connect(replacement));
		assertEquals(1, notificationsShowing());
		// and the one that was accepted still is
		assertTrue(connect(selfSigned));
	}

	@Test
	public void acceptingTheReplacementAllowsItAndNotTheOldOne() throws Exception {
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));
		assertFalse(connect(replacement));
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(replacement));

		assertTrue(connect(replacement));
		assertFalse(connect(selfSigned));
	}

	@Test
	public void acceptingAllFutureCertificatesAllowsAnyOfThem() throws Exception {
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));
		EasyX509TrustManager.unpinHost(context, HOST, PORT);

		assertTrue(connect(selfSigned));
		assertTrue(connect(replacement));
		assertEquals(0, notificationsShowing());
	}

	@Test
	public void acceptingACertificateAgainStopsAcceptingAllOfThem() throws Exception {
		EasyX509TrustManager.unpinHost(context, HOST, PORT);
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));

		assertTrue(connect(selfSigned));
		assertFalse(connect(replacement));
	}

	@Test
	public void whatWasAcceptedForOnePortDoesNotApplyToAnother() throws Exception {
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));

		assertTrue(connect(selfSigned, PORT));
		assertFalse(connect(selfSigned, 8443));
	}

	/**
	 * When a server moves from a self-signed certificate to a real one, the
	 * old acceptance is dropped, so going back to self-signed asks again.
	 */
	@Test
	public void aTrustedCertificateReplacesAnEarlierAcceptance() throws Exception {
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));
		assertTrue(connect(trusted));

		assertFalse(connect(selfSigned));
		assertEquals(1, notificationsShowing());
	}

	/**
	 * Answering the question means it is no longer showing, so a later
	 * change of certificate asks again and is not taken for a repeat.
	 */
	@Test
	public void afterTheUserAnswersTheyCanBeAskedAgain() throws Exception {
		assertFalse(connect(selfSigned));
		EasyX509TrustManager.pinCertificate(context, HOST, PORT, fingerprint(selfSigned));
		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		manager.cancelAll();

		assertFalse(connect(replacement));
		assertEquals(1, notificationsShowing());
	}

	/**
	 * A certificate a CA vouches for was accepted for any host at all, so
	 * anyone holding a valid certificate for a site of their own could stand in
	 * for the server and be given the password.
	 */
	@Test
	public void aTrustedCertificateForAnotherHostIsRefused() throws Exception {
		try {
			assertFalse(trustManager.verify("dav.example.net", PORT, new X509Certificate[] { trusted }));
			assertFalse(trustManager.verify("test.invalid", PORT, new X509Certificate[] { trusted }));
			assertFalse(trustManager.verify("trusted.test.invalid.example.net", PORT, new X509Certificate[] { trusted }));
		}
		finally {
			for ( String host : new String[] { "dav.example.net", "test.invalid", "trusted.test.invalid.example.net" } )
				EasyX509TrustManager.clearPendingNotification(host, PORT);
		}
	}

	/** It is as good as one nobody vouches for, and is dealt with the same way. */
	@Test
	public void theUserIsAskedAboutATrustedCertificateForAnotherHost() throws Exception {
		try {
			assertFalse(trustManager.verify("dav.example.net", PORT, new X509Certificate[] { trusted }));
			assertEquals(1, notificationsShowing());

			EasyX509TrustManager.pinCertificate(context, "dav.example.net", PORT, fingerprint(trusted));
			assertTrue(trustManager.verify("dav.example.net", PORT, new X509Certificate[] { trusted }));
		}
		finally {
			EasyX509TrustManager.clearPendingNotification("dav.example.net", PORT);
		}
	}

	/** Nor does it clear what the user decided about that other host. */
	@Test
	public void aTrustedCertificateForAnotherHostLeavesThePinAlone() throws Exception {
		try {
			EasyX509TrustManager.pinCertificate(context, "dav.example.net", PORT, fingerprint(selfSigned));

			assertFalse(trustManager.verify("dav.example.net", PORT, new X509Certificate[] { trusted }));
			assertTrue(trustManager.verify("dav.example.net", PORT, new X509Certificate[] { selfSigned }));
		}
		finally {
			EasyX509TrustManager.clearPendingNotification("dav.example.net", PORT);
		}
	}

	@Test
	public void nothingIsAllowedWithoutACertificate() throws Exception {
		assertFalse(trustManager.verify(HOST, PORT, null));
		assertFalse(trustManager.verify(HOST, PORT, new X509Certificate[0]));
		assertFalse(trustManager.verify(null, PORT, new X509Certificate[] { trusted }));
	}

	/**
	 * What had been seen was kept for each thread, between the handshake and
	 * the decision.  A connection whose handshake was on another thread, or
	 * which resumed a session and had none, was refused or was judged by the
	 * certificate of whatever that thread had connected to last.
	 */
	@Test
	public void theDecisionIsAboutTheCertificateGivenOnWhateverThread() throws Exception {
		// This thread last saw a certificate that is trusted ...
		assertTrue(connect(trusted));

		// ... which says nothing about the next one it is asked about
		assertFalse(connect(selfSigned));

		final boolean[] allowed = { false, true };
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					allowed[0] = connect(trusted);
					allowed[1] = trustManager.verify("dav.example.net", PORT, new X509Certificate[] { selfSigned });
				}
				catch ( Exception e ) {
					throw new RuntimeException(e);
				}
			}
		});
		thread.start();
		thread.join();
		assertTrue(allowed[0]);
		assertFalse(allowed[1]);
	}

	/** The handshake is let through, because only with the host's name can the pin be looked up. */
	@Test
	public void theHandshakeAloneDecidesNothing() throws Exception {
		trustManager.checkServerTrusted(new X509Certificate[] { selfSigned }, "RSA");
		trustManager.checkServerTrusted(new X509Certificate[] { trusted }, "RSA");
		assertEquals(0, notificationsShowing());
	}
}
