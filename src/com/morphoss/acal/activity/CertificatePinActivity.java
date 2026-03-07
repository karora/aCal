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

package com.morphoss.acal.activity;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.service.connector.EasyX509TrustManager;

/**
 * Shown when a self-signed server certificate requires user verification.
 *
 * On first connection: prompts the user to accept (pin) or reject the certificate.
 * When a pinned certificate changes: prompts accept-new, accept-all-future, or reject.
 */
public class CertificatePinActivity extends Activity {

    private String hostname;
    private int port;
    private String fingerprint;
    private String oldFingerprint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificate_pin);

        Intent intent = getIntent();
        hostname     = intent.getStringExtra(Constants.CERT_EXTRA_HOSTNAME);
        port         = intent.getIntExtra(Constants.CERT_EXTRA_PORT, 443);
        String subject  = intent.getStringExtra(Constants.CERT_EXTRA_SUBJECT);
        fingerprint  = intent.getStringExtra(Constants.CERT_EXTRA_FINGERPRINT);
        String expiry   = intent.getStringExtra(Constants.CERT_EXTRA_EXPIRY);
        oldFingerprint  = intent.getStringExtra(Constants.CERT_EXTRA_OLD_FINGERPRINT);

        boolean isCertChanged = (oldFingerprint != null);

        ((TextView) findViewById(R.id.cert_title)).setText(
                isCertChanged ? R.string.cert_title_changed : R.string.cert_title_new);

        ((TextView) findViewById(R.id.cert_server)).setText(hostname + ":" + port);
        ((TextView) findViewById(R.id.cert_subject)).setText(subject);
        ((TextView) findViewById(R.id.cert_expiry)).setText(expiry);
        ((TextView) findViewById(R.id.cert_fingerprint)).setText(
                EasyX509TrustManager.formatFingerprint(fingerprint));

        View oldSection = findViewById(R.id.cert_old_fingerprint_section);
        if (isCertChanged) {
            oldSection.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.cert_old_fingerprint)).setText(
                    EasyX509TrustManager.formatFingerprint(oldFingerprint));
        } else {
            oldSection.setVisibility(View.GONE);
        }

        Button acceptAllButton = findViewById(R.id.cert_accept_all);
        acceptAllButton.setVisibility(isCertChanged ? View.VISIBLE : View.GONE);

        findViewById(R.id.cert_accept).setOnClickListener(v -> {
            EasyX509TrustManager.pinCertificate(this, hostname, port, fingerprint);
            cancelNotification();
            finish();
        });

        acceptAllButton.setOnClickListener(v -> {
            EasyX509TrustManager.unpinHost(this, hostname, port);
            cancelNotification();
            finish();
        });

        findViewById(R.id.cert_reject).setOnClickListener(v -> {
            EasyX509TrustManager.clearPendingNotification(hostname, port);
            cancelNotification();
            finish();
        });
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(EasyX509TrustManager.notificationId(hostname, port));
    }
}
