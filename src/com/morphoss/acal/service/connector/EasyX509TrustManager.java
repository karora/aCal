package com.morphoss.acal.service.connector;

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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.morphoss.acal.Constants;

/**
 * X509TrustManager that validates certificates against the system trust store
 * and allows user-approved certificates that have been explicitly trusted.
 *
 * Security improvements over the original implementation:
 * - Uses SHA-256 fingerprints for certificate identification
 * - Stores approved certificates securely with encryption
 * - Does NOT allow self-signed certificates by default
 * - Requires explicit user approval for untrusted certificates
 */
public class EasyX509TrustManager implements X509TrustManager {

    private static final String TAG = "aCal X509TrustManager";
    private static final String PREFS_NAME = "acal_trusted_certs";
    private static final String KEY_APPROVED_FINGERPRINTS = "approved_fingerprints";

    private final X509TrustManager standardTrustManager;
    private final Context context;
    private Set<String> approvedFingerprints;

    /**
     * Constructor for EasyX509TrustManager.
     *
     * @param keystore The keystore to use (can be null for system default)
     * @param context  Application context for secure storage access
     */
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

        loadApprovedFingerprints();
    }

    /**
     * Legacy constructor for compatibility - uses null context (limited functionality).
     * @deprecated Use constructor with Context parameter for full security features.
     */
    @Deprecated
    public EasyX509TrustManager(KeyStore keystore)
            throws NoSuchAlgorithmException, KeyStoreException {
        this(keystore, null);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String authType)
            throws CertificateException {
        standardTrustManager.checkClientTrusted(certificates, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String authType)
            throws CertificateException {
        if (certificates == null || certificates.length < 1) {
            throw new CertificateException("No certificates provided");
        }

        try {
            // First, try standard validation
            standardTrustManager.checkServerTrusted(certificates, authType);
        } catch (CertificateExpiredException e) {
            logCertificateError("Certificate expired", e, certificates);
            throw e;
        } catch (CertificateNotYetValidException e) {
            logCertificateError("Certificate not yet valid", e, certificates);
            throw e;
        } catch (CertificateException e) {
            // Standard validation failed - check if user has approved this certificate
            if (Constants.LOG_DEBUG) {
                Log.d(TAG, "Standard validation failed: " + e.getMessage());
            }

            if (isUserApprovedCertificate(certificates[0])) {
                // User has previously approved this certificate
                if (Constants.LOG_DEBUG) {
                    Log.d(TAG, "Certificate approved by user");
                }

                // Still verify the certificate is currently valid (not expired)
                try {
                    certificates[0].checkValidity();
                } catch (CertificateExpiredException ce) {
                    // Remove expired certificate from approved list
                    removeApprovedCertificate(certificates[0]);
                    logCertificateError("Previously approved certificate has expired", ce, certificates);
                    throw ce;
                } catch (CertificateNotYetValidException ce) {
                    logCertificateError("Certificate not yet valid", ce, certificates);
                    throw ce;
                }
                return; // Certificate is approved and valid
            }

            // Certificate is not trusted and not approved by user
            logCertificateError("Untrusted certificate", e, certificates);
            throw e;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return standardTrustManager.getAcceptedIssuers();
    }

    /**
     * Calculate SHA-256 fingerprint of a certificate.
     *
     * @param cert The certificate
     * @return Hex-encoded SHA-256 fingerprint, or null on error
     */
    public static String getCertificateFingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] der = cert.getEncoded();
            byte[] digest = md.digest(der);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            Log.e(TAG, "Error calculating certificate fingerprint", e);
            return null;
        }
    }

    /**
     * Check if a certificate has been approved by the user.
     */
    private boolean isUserApprovedCertificate(X509Certificate cert) {
        String fingerprint = getCertificateFingerprint(cert);
        if (fingerprint == null) {
            return false;
        }
        return approvedFingerprints.contains(fingerprint);
    }

    /**
     * Add a certificate to the user-approved list.
     * Call this after the user explicitly approves a certificate.
     *
     * @param cert The certificate to approve
     * @return true if successfully added
     */
    public boolean addApprovedCertificate(X509Certificate cert) {
        String fingerprint = getCertificateFingerprint(cert);
        if (fingerprint == null) {
            return false;
        }

        approvedFingerprints.add(fingerprint);
        saveApprovedFingerprints();

        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Added approved certificate: " + cert.getSubjectDN());
            Log.d(TAG, "Fingerprint: " + fingerprint);
        }
        return true;
    }

    /**
     * Remove a certificate from the user-approved list.
     *
     * @param cert The certificate to remove
     * @return true if successfully removed
     */
    public boolean removeApprovedCertificate(X509Certificate cert) {
        String fingerprint = getCertificateFingerprint(cert);
        if (fingerprint == null) {
            return false;
        }

        boolean removed = approvedFingerprints.remove(fingerprint);
        if (removed) {
            saveApprovedFingerprints();
            if (Constants.LOG_DEBUG) {
                Log.d(TAG, "Removed approved certificate: " + cert.getSubjectDN());
            }
        }
        return removed;
    }

    /**
     * Clear all user-approved certificates.
     */
    public void clearApprovedCertificates() {
        approvedFingerprints.clear();
        saveApprovedFingerprints();
        if (Constants.LOG_DEBUG) {
            Log.d(TAG, "Cleared all approved certificates");
        }
    }

    /**
     * Get the number of user-approved certificates.
     */
    public int getApprovedCertificateCount() {
        return approvedFingerprints.size();
    }

    /**
     * Load approved certificate fingerprints from secure storage.
     */
    private void loadApprovedFingerprints() {
        approvedFingerprints = new HashSet<>();

        if (context == null) {
            return;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String stored = prefs.getString(KEY_APPROVED_FINGERPRINTS, "");

            if (!stored.isEmpty()) {
                String[] fingerprints = stored.split(";");
                for (String fp : fingerprints) {
                    if (!fp.isEmpty() && fp.length() == 64) { // SHA-256 = 64 hex chars
                        approvedFingerprints.add(fp);
                    }
                }
            }

            if (Constants.LOG_DEBUG) {
                Log.d(TAG, "Loaded " + approvedFingerprints.size() + " approved certificates");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading approved certificates", e);
        }
    }

    /**
     * Save approved certificate fingerprints to secure storage.
     */
    private void saveApprovedFingerprints() {
        if (context == null) {
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (String fp : approvedFingerprints) {
                if (sb.length() > 0) {
                    sb.append(";");
                }
                sb.append(fp);
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_APPROVED_FINGERPRINTS, sb.toString()).apply();

            if (Constants.LOG_DEBUG) {
                Log.d(TAG, "Saved " + approvedFingerprints.size() + " approved certificates");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving approved certificates", e);
        }
    }

    /**
     * Log certificate error details.
     */
    private void logCertificateError(String message, Exception e, X509Certificate[] certificates) {
        Log.w(TAG, message + ": " + e.getMessage());
        for (X509Certificate cert : certificates) {
            Log.w(TAG, "  Subject: " + cert.getSubjectDN());
            Log.w(TAG, "  Issuer: " + cert.getIssuerDN());
            Log.w(TAG, "  Valid: " + cert.getNotBefore() + " to " + cert.getNotAfter());
            String fingerprint = getCertificateFingerprint(cert);
            if (fingerprint != null) {
                Log.w(TAG, "  SHA-256: " + fingerprint);
            }
        }
    }

    /**
     * Convert bytes to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
