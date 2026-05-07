package org.davical.acal.service.connector;

/*
 * Copyright (C) 2011 Morphoss Ltd
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
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.davical.acal.Constants;
import org.davical.acal.R;
import org.davical.acal.activity.CertificatePinActivity;

/**
 * X509TrustManager implementing TOFU (trust-on-first-use) certificate pinning
 * for self-signed / CA-invalid server certificates.
 *
 * CA-valid certificates (e.g. Let's Encrypt) pass through standard validation
 * with no pinning and no user interaction — including transparent cert rotation.
 *
 * Self-signed certificates trigger a user prompt on first connection.  The user
 * can accept (pin the certificate) or reject.  If the pinned certificate changes,
 * the user is prompted again and can accept the new cert or choose to stop pinning.
 */
public class EasyX509TrustManager implements X509TrustManager {

    private static final String TAG = "aCal X509TrustManager";
    private static final String PREFS_NAME = "acal_cert_pins";
    private static final String PIN_PREFIX = "pin:";
    private static final String UNPIN_PREFIX = "unpin:";

    // Hosts with a notification already showing — don't fire duplicates.
    private static final Set<String> pendingNotifications =
            Collections.synchronizedSet(new HashSet<>());

    // Thread-locals carry cert state from checkServerTrusted() to verifyPin(),
    // which is called on the same thread by OkHttp's hostname verifier.
    private static final ThreadLocal<Boolean> tLastWasCaValid = new ThreadLocal<>();
    private static final ThreadLocal<X509Certificate[]> tLastChain = new ThreadLocal<>();

    private final X509TrustManager standardTrustManager;
    private final Context context;

    public EasyX509TrustManager(KeyStore keystore, Context context)
            throws NoSuchAlgorithmException, KeyStoreException {
        super();
        this.context = context;
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keystore);
        TrustManager[] trustmanagers = factory.getTrustManagers();
        if (trustmanagers.length == 0) {
            throw new NoSuchAlgorithmException("No trust manager found");
        }
        this.standardTrustManager = (X509TrustManager) trustmanagers[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String authType)
            throws CertificateException {
        standardTrustManager.checkClientTrusted(certificates, authType);
    }

    /**
     * Records the chain and whether CA validation passed in thread-locals.
     * Does NOT throw for CA-invalid certs — verifyPin() makes the final decision.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String authType)
            throws CertificateException {
        if (certificates == null || certificates.length < 1) {
            throw new CertificateException("No certificates provided");
        }
        tLastChain.set(certificates);
        try {
            standardTrustManager.checkServerTrusted(certificates, authType);
            tLastWasCaValid.set(true);
        } catch (CertificateException e) {
            tLastWasCaValid.set(false);
            // Don't throw — verifyPin() will accept or reject based on pinning state.
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return standardTrustManager.getAcceptedIssuers();
    }

    /**
     * Called by OkHttp's hostname verifier immediately after checkServerTrusted().
     * Implements the TOFU pinning decision for CA-invalid certs.
     */
    public boolean verifyPin(String hostname, int port) {
        X509Certificate[] chain = tLastChain.get();
        Boolean caValid = tLastWasCaValid.get();
        if (chain == null || caValid == null) return false;

        if (caValid) {
            // CA validation passed — always trust.  Clear any stale pin in case
            // the server upgraded from self-signed to a real certificate.
            clearPin(hostname, port);
            return true;
        }

        // CA-invalid (self-signed) from here:
        String hostKey = hostname + ":" + port;
        X509Certificate cert = chain[0];
        String currentFingerprint = getCertificateFingerprint(cert);
        if (currentFingerprint == null) return false;

        if (isUnpinned(hostname, port)) {
            return true; // User chose "accept all future changes"
        }

        String storedPin = getStoredPin(hostname, port);

        if (storedPin == null) {
            // First connection with a self-signed cert: prompt the user.
            if (!pendingNotifications.contains(hostKey)) {
                pendingNotifications.add(hostKey);
                fireFirstConnectNotification(hostname, port, cert);
            }
            return false;
        }

        if (storedPin.equals(currentFingerprint)) {
            return true; // Fingerprint matches stored pin.
        }

        // Fingerprint mismatch: cert has changed.
        if (!pendingNotifications.contains(hostKey)) {
            pendingNotifications.add(hostKey);
            fireCertChangedNotification(hostname, port, cert, storedPin);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Static methods called by CertificatePinActivity
    // -------------------------------------------------------------------------

    /** Store a pin for this host and mark any pending notification as resolved. */
    public static void pinCertificate(Context context, String hostname, int port,
                                      String fingerprint) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
             .putString(PIN_PREFIX + hostname + ":" + port, fingerprint)
             .remove(UNPIN_PREFIX + hostname + ":" + port)
             .apply();
        pendingNotifications.remove(hostname + ":" + port);
        if (Constants.LOG_DEBUG) Log.d(TAG, "Pinned cert for " + hostname + ":" + port);
    }

    /** Mark host as unpinned (accept any cert) and clear any pending notification. */
    public static void unpinHost(Context context, String hostname, int port) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
             .remove(PIN_PREFIX + hostname + ":" + port)
             .putString(UNPIN_PREFIX + hostname + ":" + port, "true")
             .apply();
        pendingNotifications.remove(hostname + ":" + port);
        if (Constants.LOG_DEBUG) Log.d(TAG, "Unpinned host " + hostname + ":" + port);
    }

    /** Called when the user dismisses the cert prompt without choosing. */
    public static void clearPendingNotification(String hostname, int port) {
        pendingNotifications.remove(hostname + ":" + port);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String getStoredPin(String hostname, int port) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PIN_PREFIX + hostname + ":" + port, null);
    }

    private boolean isUnpinned(String hostname, int port) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains(UNPIN_PREFIX + hostname + ":" + port);
    }

    private void clearPin(String hostname, int port) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(PIN_PREFIX + hostname + ":" + port)) {
            prefs.edit().remove(PIN_PREFIX + hostname + ":" + port).apply();
            Log.i(TAG, "Cleared stale pin for " + hostname + ":" + port
                    + " (cert is now CA-valid)");
        }
    }

    private void fireFirstConnectNotification(String hostname, int port, X509Certificate cert) {
        if (context == null) return;
        String fingerprint = getCertificateFingerprint(cert);
        String subject = cert.getSubjectDN().getName();
        String expiry = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.getNotAfter());

        Intent activityIntent = new Intent(context, CertificatePinActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityIntent.putExtra(Constants.CERT_EXTRA_HOSTNAME, hostname);
        activityIntent.putExtra(Constants.CERT_EXTRA_PORT, port);
        activityIntent.putExtra(Constants.CERT_EXTRA_SUBJECT, subject);
        activityIntent.putExtra(Constants.CERT_EXTRA_FINGERPRINT, fingerprint);
        activityIntent.putExtra(Constants.CERT_EXTRA_EXPIRY, expiry);

        postCertNotification(hostname, port, activityIntent,
                context.getString(R.string.cert_notif_first_title));
        Log.i(TAG, "Fired first-connect cert notification for " + hostname + ":" + port);
    }

    private void fireCertChangedNotification(String hostname, int port,
                                              X509Certificate cert, String oldFingerprint) {
        if (context == null) return;
        String newFingerprint = getCertificateFingerprint(cert);
        String subject = cert.getSubjectDN().getName();
        String expiry = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.getNotAfter());

        Intent activityIntent = new Intent(context, CertificatePinActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityIntent.putExtra(Constants.CERT_EXTRA_HOSTNAME, hostname);
        activityIntent.putExtra(Constants.CERT_EXTRA_PORT, port);
        activityIntent.putExtra(Constants.CERT_EXTRA_SUBJECT, subject);
        activityIntent.putExtra(Constants.CERT_EXTRA_FINGERPRINT, newFingerprint);
        activityIntent.putExtra(Constants.CERT_EXTRA_EXPIRY, expiry);
        activityIntent.putExtra(Constants.CERT_EXTRA_OLD_FINGERPRINT, oldFingerprint);

        postCertNotification(hostname, port, activityIntent,
                context.getString(R.string.cert_notif_changed_title));
        Log.i(TAG, "Fired cert-changed notification for " + hostname + ":" + port);
    }

    private void postCertNotification(String hostname, int port,
                                      Intent activityIntent, String title) {
        int notifId = notificationId(hostname, port);
        PendingIntent pi = PendingIntent.getActivity(context, notifId, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(hostname)
                .setContentIntent(pi)
                .setAutoCancel(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(Constants.CERT_PIN_NOTIFICATION_CHANNEL_ID);
        } else {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());
    }

    // -------------------------------------------------------------------------
    // Public utilities
    // -------------------------------------------------------------------------

    public static int notificationId(String hostname, int port) {
        return (Math.abs((hostname + ":" + port).hashCode()) % 100_000) + 500_000;
    }

    public static String getCertificateFingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            Log.e(TAG, "Error calculating certificate fingerprint", e);
            return null;
        }
    }

    /** Format a 64-char hex fingerprint as colon-separated pairs, 8 per line. */
    public static String formatFingerprint(String hex) {
        if (hex == null || hex.length() != 64) return hex;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i += 2) {
            if (i > 0) sb.append(i % 16 == 0 ? "\n" : ":");
            sb.append(hex, i, i + 2);
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
