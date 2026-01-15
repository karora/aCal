package com.morphoss.acal.service.connector;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import com.morphoss.acal.Constants;
import com.morphoss.acal.service.aCalService;

/**
 * Connection pool manager for HTTP requests using OkHttp.
 * Provides a shared OkHttpClient instance with custom SSL/TLS handling.
 */
public class AcalConnectionPool {

	private static final String TAG = "AcalConnectionPool";

	public static final int DEFAULT_BUFFER_SIZE = 4096;
	private static final int MAX_IDLE_CONNECTIONS = 5;
	private static final int KEEP_ALIVE_DURATION_MINUTES = 5;

	private static OkHttpClient httpClient = null;
	private static Context appContext = null;
	private static String userAgent = null;

	private static int socketTimeOut = 60000;
	private static int connectionTimeOut = 30000;

	/**
	 * Initialize the connection pool with application context.
	 * Should be called once during app initialization.
	 * @param context Application context for certificate storage
	 */
	public static void initialize(Context context) {
		appContext = context.getApplicationContext();
	}

	/**
	 * Get or create the shared OkHttpClient instance.
	 * @return OkHttpClient configured with custom SSL and connection pooling
	 */
	public static synchronized OkHttpClient getHttpClient() {
		if (httpClient == null) {
			httpClient = createClient();
		}
		return httpClient;
	}

	/**
	 * Set the timeouts to use for subsequent requests, in milliseconds.
	 * If timeouts change, the client will be rebuilt.
	 * @param newSocketTimeOut Socket/read timeout in milliseconds
	 * @param newConnectionTimeOut Connection timeout in milliseconds
	 */
	public static synchronized void setTimeOuts(int newSocketTimeOut, int newConnectionTimeOut) {
		if (socketTimeOut != newSocketTimeOut || connectionTimeOut != newConnectionTimeOut) {
			socketTimeOut = newSocketTimeOut;
			connectionTimeOut = newConnectionTimeOut;
			// Rebuild client with new timeouts
			httpClient = createClient();
		}
	}

	/**
	 * Create a new OkHttpClient with the current configuration.
	 */
	private static OkHttpClient createClient() {
		try {
			// Create trust manager that delegates to EasyX509TrustManager
			X509TrustManager trustManager = new EasyX509TrustManager(null, appContext);

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[]{trustManager}, null);

			return new OkHttpClient.Builder()
				.connectTimeout(connectionTimeOut, TimeUnit.MILLISECONDS)
				.readTimeout(socketTimeOut, TimeUnit.MILLISECONDS)
				.writeTimeout(socketTimeOut, TimeUnit.MILLISECONDS)
				.sslSocketFactory(sslContext.getSocketFactory(), trustManager)
				.hostnameVerifier((hostname, session) -> true) // Hostname verification handled by TrustManager
				.connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION_MINUTES, TimeUnit.MINUTES))
				.followRedirects(false) // Manual redirect handling in AcalRequestor
				.followSslRedirects(false)
				.build();
		}
		catch (NoSuchAlgorithmException | KeyStoreException e) {
			Log.e(TAG, "Failed to create OkHttpClient", e);
			throw new RuntimeException("Failed to create OkHttpClient", e);
		}
		catch (Exception e) {
			Log.e(TAG, "Unexpected error creating OkHttpClient", e);
			throw new RuntimeException("Failed to create OkHttpClient", e);
		}
	}

	/**
	 * Get the User-Agent string for HTTP requests.
	 * @return User-Agent string including app version and device info
	 */
	public static String getUserAgent() {
		if (userAgent == null) {
			userAgent = aCalService.aCalVersion;

			// User-Agent: aCal/0.3 (google; Nexus One; passion; HTC; passion; FRG83D) Android/2.2.1 (75603)
			userAgent += " (" + Build.BRAND + "; " + Build.MODEL + "; " + Build.PRODUCT + "; "
						+ Build.MANUFACTURER + "; " + Build.DEVICE + "; " + Build.DISPLAY + "; " + Build.BOARD + ") "
						+ " Android/" + Build.VERSION.RELEASE + " (" + Build.VERSION.INCREMENTAL + ")";
		}
		return userAgent;
	}

}
