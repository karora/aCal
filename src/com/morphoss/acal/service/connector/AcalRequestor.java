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
 *
 */

package com.morphoss.acal.service.connector;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import android.content.ContentValues;
import android.os.Build;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.StaticHelpers;
import com.morphoss.acal.activity.serverconfig.AuthenticationFailure;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.xml.DavNode;
import com.morphoss.acal.xml.DavXmlTreeBuilder;

public class AcalRequestor {

	final private static String TAG = "AcalRequestor";

	private boolean initialised = false;
	
	// Basic URI components
	private String hostName = null;
	private String path = "/";
	private String protocol = "http";
	private int port = 0;
	private String method = "PROPFIND";

	// Authentication crap.
	protected boolean authRequired = false;
	protected int authType  = Servers.AUTH_NONE; 
	protected Header wwwAuthenticate = null;
	protected String authRealm = null;
	protected String nonce = null;
	protected String opaque = null;
	protected String cnonce = null;
	protected String qop = null;
	protected int authNC = 0;
	protected String algorithm = null;

	private String username = null;
	private String password = null;
	
	private static String userAgent = null;

	private HttpParams httpParams;
	private HttpClient httpClient;
	private ThreadSafeClientConnManager connManager;
	private SchemeRegistry schemeRegistry;
	private Header responseHeaders[];
	private int statusCode = -1;
	private int connectionTimeOut = 60000;
	private int redirectLimit = 5;
	private int redirectCount = 0;

	
	public AcalRequestor() {
	}

	public AcalRequestor( String hostIn, Integer proto, Integer portIn, String pathIn, String user, String pass ) {
		hostName = hostIn;
		setPortProtocol(portIn,proto);
		setPath(pathIn);
		username = user;
		password = pass;

		initialise();
	}

	public static AcalRequestor fromServerValues( ContentValues cvServerData ) {
		AcalRequestor result = new AcalRequestor();
		result.setFromServer(cvServerData);
		return result;
	}

	
	public void setFromServer( ContentValues cvServerData ) {
		String hostName = cvServerData.getAsString(Servers.HOSTNAME);
		if ( hostName == null || hostName.equals("") ) {
			hostName = cvServerData.getAsString(Servers.SUPPLIED_DOMAIN);
		}

		String requestPath = cvServerData.getAsString(Servers.PRINCIPAL_PATH);
		if ( requestPath == null || requestPath.equals("") )
			requestPath = cvServerData.getAsString(Servers.SUPPLIED_PATH);

		this.hostName = hostName;
		setPath(requestPath);
		setPortProtocol(cvServerData.getAsInteger(Servers.PORT),cvServerData.getAsInteger(Servers.USE_SSL));

		username = cvServerData.getAsString(Servers.USERNAME);
		password = cvServerData.getAsString(Servers.PASSWORD);

		if ( !initialised ) initialise();
	}

	
	private void initialise() {
		if ( userAgent == null ) {
			try {
				userAgent = aCalService.aCalVersion;
			}
			catch ( Exception e ){
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Couldn't assign userAgent from aCalService.aCalVersion");
				if ( Constants.LOG_DEBUG ) Log.d(TAG,Log.getStackTraceString(e));
			}
	
	// User-Agent: aCal/0.3 (google; Nexus One; passion; HTC; passion; FRG83D)  Android/2.2.1 (75603)
			userAgent += " (" + Build.BRAND + "; " + Build.MODEL + "; " + Build.PRODUCT + "; "
						+ Build.MANUFACTURER + "; " + Build.DEVICE + "; " + Build.DISPLAY + "; " + Build.BOARD + ") "
						+ " Android/" + Build.VERSION.RELEASE + " (" + Build.VERSION.INCREMENTAL + ")";
		}

		httpParams = defaultHttpParams();

		schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		Scheme httpsScheme = new Scheme("https",  new EasySSLSocketFactory(), 443);
		schemeRegistry.register(httpsScheme);

		connManager = new ThreadSafeClientConnManager(httpParams, schemeRegistry);
		httpClient = new DefaultHttpClient(connManager, httpParams);

		initialised = true;
	}


	
	public void applySettings(ContentValues cvServerData) {
		cvServerData.put(Servers.HOSTNAME, hostName);
		cvServerData.put(Servers.USE_SSL, (protocol.equals("https")?1:0));
		cvServerData.put(Servers.PORT, port);
		cvServerData.put(Servers.PRINCIPAL_PATH, path);
		cvServerData.put(Servers.AUTH_TYPE, authType );
	}

	
	
	public Header[] getResponseHeaders() {
		return this.responseHeaders;
	}
	
	public int getStatusCode() {
		return this.statusCode;
	}
	
	public void interpretUriString(String uriString) {
		// Match a URL, including an ipv6 address like http://[DEAD:BEEF:CAFE:F00D::]:8008/
		final Pattern uriMatcher = Pattern.compile(
					"^(?:(https?)://)?" + // Protocol
					"(" + // host spec
					"(?:(?:[a-z0-9-]+[.]){2,7}(?:[a-z0-9-]+))" +  // Hostname or IPv4 address
					"|(?:\\[(?:[0-9a-f]{0,4}:)+(?:[0-9a-f]{0,4})?\\])" + // IPv6 address
					")" +
					"(?:[:]([0-9]{2,5}))?" + // Port number
					"(/.*)?$" // Path bit.
					,Pattern.CASE_INSENSITIVE | Pattern.DOTALL );  

		final Pattern pathMatcher = Pattern.compile("^(/.*)$");
		
		Matcher m = uriMatcher.matcher(uriString);
		if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Interpreting '"+uriString+"'");
		if ( m.matches() ) {
			if ( m.group(1) != null && !m.group(1).equals("") ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found protocol '"+m.group(1)+"'");
				protocol = m.group(1);
				if ( m.group(3) == null || m.group(3).equals("") ) {
					port = (protocol.equals("http") ? 80 : 443);
				}
			}
			if ( m.group(2) != null ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found hostname '"+m.group(2)+"'");
				hostName = m.group(2);
			}
			if ( m.group(3) != null && !m.group(3).equals("") ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found port '"+m.group(3)+"'");
				port = Integer.parseInt(m.group(3));
				if ( m.group(1) != null && (port == 0 || port == 80 || port == 443) ) {
					port = (protocol.equals("http") ? 80 : 443);
				}
			}
			if ( m.group(4) != null && !m.group(4).equals("") ) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found redirect path '"+m.group(4)+"'");
				setPath(m.group(4));
			}
		}
		else {
			m = pathMatcher.matcher(uriString);
			if (m.find()) {
				if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Found simple redirect path '"+m.group(1)+"'");
				setPath( m.group(1) );
			}
		}
	}


	/**
	 * When a request fails with a 401 Unauthorized you can call this with the content
	 * of the WWW-Authenticate header in the response and it will modify the URI so that
	 * if you repeat the request the correct authentication should be used.
	 * 
	 * If you then get a 401, and this gets called again on that same Uri, it will throw
	 * an AuthenticationFailure exception rather than continue futilely.
	 * 
	 * @param authRequestHeader
	 * @throws AuthenticationFailure
	 */
	public void interpretRequestedAuth( Header authRequestHeader ) throws AuthenticationFailure {
		// Adjust our authentication setup so the next request will be able
		// to send the correct authentication headers...

		// 'WWW-Authenticate: Digest realm="DAViCal CalDAV Server", qop="auth", nonce="55a1a0c53c0f337e4675befabeff6a122b5b78de", opaque="52295deb26cc99c2dcc6614e70ed471f7a163e7a", algorithm="MD5"'

		if ( Constants.LOG_VERBOSE )
			Log.v(TAG,"Interpreting '"+authRequestHeader+"'");

		for( HeaderElement he : authRequestHeader.getElements() ) {
			if ( Constants.LOG_VERBOSE )
				Log.v(TAG,"Interpreting Element: '"+he.toString()+"' ("+he.getName()+":"+he.getValue()+")");
			if ( he.getName().equals("Digest realm") ) { 
				authType = Servers.AUTH_DIGEST;
				authRealm = he.getValue();
			}
			else if ( he.getName().equals("Basic realm") ) { 
				authType = Servers.AUTH_BASIC;
				authRealm = he.getValue();
			}
			else if ( he.getName().equals("qop") ) {
				qop = "auth";
			}
			else if ( he.getName().equals("nonce") ) {
				nonce = he.getValue();
			}
			else if ( he.getName().equals("opaque") ) {
				opaque = he.getValue();
			}
			else if ( he.getName().equals("algorithm") ) {
				algorithm = "MD5";
			}
			
		}
		authRequired = true;
	}

	
	private String md5( String in ) {
		// Create MD5 Hash
		MessageDigest digest;
		try {
			digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(in.getBytes());
			return StaticHelpers.toHexString(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			Log.e(TAG, e.getMessage());
			Log.v(TAG, Log.getStackTraceString(e));
		}
	    return "";
	}

	
	private Header buildAuthHeader() throws AuthenticationFailure {
		String authValue;
		switch( authType ) {
			case Servers.AUTH_BASIC:
				authValue = String.format("Basic %s", Base64Coder.encodeString(username+":"+password));
				break;
			case Servers.AUTH_DIGEST:
				String A1 = md5( username + ":" + authRealm + ":" + password);
				String A2 = md5( method + ":" + path );
				cnonce = md5(userAgent);
				String printNC = String.format("%08x", ++authNC);
				String responseString = A1+":"+nonce+":"+printNC+":"+cnonce+":auth:"+A2;
				if ( Constants.LOG_VERBOSE && Constants.debugDavCommunication )
					Log.v(TAG, "DigestDebugging: '"+responseString+"'" );
				String response = md5(responseString);
				authValue = String.format("Digest realm=\"%s\", username=\"%s\", nonce=\"%s\", uri=\"%s\""
							+ ", response=\"%s\", algorithm=\"MD5\", cnonce=\"%s\", opaque=\"%s\", nc=\"%s\""
							+ ", qop=\"auth\"",
							authRealm, username, nonce, path,
							response, cnonce, opaque, printNC );
				break;
			default:
				throw new AuthenticationFailure("Unknown authentication type");
		}
		return new BasicHeader("Authorization", authValue );
	}

	
	public String getPath() {
		return path;
	}

	
	public int getAuthType() {
		return authType;
	}

	
	public void setPortProtocol(Integer newPort, Integer newProtocol) {
		protocol = (newProtocol == null || newProtocol != 1 ? "http" : "https");
		if ( newPort == null || newPort < 1 || newPort > 65535 || newPort == 80 || newPort == 443 )
			port = (protocol.equals("http") ? 80 : 443);
		else
			port = newPort;
	}

	
	public void setTimeOut(int newTimeOut) {
		if ( connectionTimeOut == newTimeOut ) return;
		connectionTimeOut = newTimeOut;
		HttpParams params = httpClient.getParams();
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeOut);
		httpClient = new DefaultHttpClient(connManager, httpParams);
	}

	
	public void setPath(String newPath) {
		if ( newPath == null || newPath.equals("") ) {
			path = "/";
			return;
		}
		if ( !newPath.substring(0, 1).equals("/") ) {
			path = "/" + newPath;
		}
		else
			path = newPath;
	}

	
	public String fullUrl() {
		return protocol
				+ "://"
				+ hostName
				+ ((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443) ? "" : ":"+Integer.toString(port))
				+ path;
	}

	
	public String getAuthTypeName() {
		switch (authType) {
			case Servers.AUTH_BASIC:	return "Basic";
			case Servers.AUTH_DIGEST:	return "Digest";
			default:					return "No";
		}
	}

	
	private String getLocationHeader() {
		for( Header h : responseHeaders ) {
			if (Constants.LOG_DEBUG && Constants.debugDavCommunication)
				Log.d(TAG, "Header: " + h.getName() + ":" + h.getValue());
			if (h.getName().equalsIgnoreCase("Location"))
				return h.getValue();
		}
		return "";
	}

	
	private Header getAuthHeader() {
		for( Header h : responseHeaders ) {
			if (Constants.LOG_DEBUG && Constants.debugDavCommunication)
				Log.d(TAG, "Header: " + h.getName() + ":" + h.getValue());
			if ( h.getName().equalsIgnoreCase("WWW-Authenticate") ) return h;
		}
		return null;
	}

	

	private HttpParams defaultHttpParams() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, HTTP.UTF_8);
		params.setParameter(CoreProtocolPNames.USER_AGENT, userAgent );
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeOut);
		params.setParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
		params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
		
		return params;
	}


	
	private InputStream sendRequest( Header[] headers, Object data )
									throws SendRequestFailedException, SSLException, AuthenticationFailure {
		long down = 0;
		long up = 0;
		long start = System.currentTimeMillis();

		statusCode = -1;
		try {
			// Create request and add headers and entity
			DavRequest request = new DavRequest(method, this.fullUrl());
//			request.addHeader(new BasicHeader("User-Agent", userAgent));
			if ( headers != null ) for (Header h : headers) request.addHeader(h);

			if ( authRequired && authType != Servers.AUTH_NONE)
				request.addHeader(buildAuthHeader());
			
			if (data != null) {
				request.setEntity(new StringEntity(data.toString(),"UTF-8"));
				up = request.getEntity().getContentLength();
			}
			

			// This trick greatly reduces the occurrence of host not found errors. 
			try { InetAddress.getByName(this.hostName); } catch (UnknownHostException e1) { }
			
			int requestPort = -1;
			String requestProtocol = this.protocol;
			if ( (this.protocol.equals("http") && this.port != 80 )
						|| ( this.protocol.equals("https") && this.port != 443 )
				) {
				requestPort = this.port;
			}

			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
				Log.d(TAG, String.format("Method: %s, Protocol: %s, Hostname: %s, Port: %d, Path: %s",
							method, requestProtocol, hostName, requestPort, path) );
			}
			HttpHost host = new HttpHost(this.hostName, requestPort, requestProtocol);

			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
				Log.d(TAG, method+" "+this.fullUrl());

				for ( Header h : request.getAllHeaders() ) {
					Log.d(TAG,"H>  "+h.getName()+":"+h.getValue() );
				}
				if (data != null) {
					Log.d(TAG, "----------------------- vvv Request Body vvv -----------------------" );
					for( String line : data.toString().split("\n") ) {
						if ( line.length() == data.toString().length() ) {
							int end;
							int length = line.length();
							for( int pos=0; pos < length; pos += 120 ) {
								end = pos+120;
								if ( end > length ) end = length;
								Log.d(TAG, "R>  " + line.substring(pos, end) );
							}
						}
						else {
							Log.d(TAG, "R>  " + line.replaceAll("\r$", "") );
						}
					}
					Log.d(TAG, "----------------------- ^^^ Request Body ^^^ -----------------------" );
				}
			}
			
			
			// Send request and get response 
			HttpResponse response = null;

			response = httpClient.execute(host,request);
			this.responseHeaders = response.getAllHeaders();
			this.statusCode = response.getStatusLine().getStatusCode();

			HttpEntity entity = response.getEntity();
			down = (entity == null ? 0 : entity.getContentLength());
			
			long finish = System.currentTimeMillis();
			double timeTaken = ((double)(finish-start))/1000.0;

			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
				Log.d(TAG, "Response: "+statusCode+", Sent: "+up+", Received: "+down+", Took: "+timeTaken+" seconds");
				for (Header h : responseHeaders) {
					Log.d(TAG,"H<  "+h.getName()+": "+h.getValue() );
				}
				if (entity != null) {
					if ( Constants.LOG_DEBUG && Constants.debugDavCommunication ) {
						Log.d(TAG, "----------------------- vvv Response Body vvv -----------------------" );
						BufferedReader r = new BufferedReader(new InputStreamReader(entity.getContent()));
						StringBuilder total = new StringBuilder();
						String line;
						while ((line = r.readLine()) != null) {
						    total.append(line);
						    total.append("\n");
							if ( line.length() > 180 ) {
								int end;
								int length = line.length();
								for( int pos=0; pos < length; pos += 120 ) {
									end = pos+120;
									if ( end > length ) end = length;
									Log.d(TAG, "R<  " + line.substring(pos, end) );
								}
							}
							else {
								Log.d(TAG, "R<  " + line.replaceAll("\r$", "") );
							}
						}
						Log.d(TAG, "----------------------- ^^^ Response Body ^^^ -----------------------" );
						return new ByteArrayInputStream( total.toString().getBytes() );
					}
				}
			}
			if (entity != null)
				return entity.getContent();

			return null;
		}
		catch (SSLException e) {
			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
				Log.d(TAG,Log.getStackTraceString(e));
			throw e;
		}
		catch (AuthenticationFailure e) {
			if ( Constants.LOG_DEBUG && Constants.debugDavCommunication )
				Log.d(TAG,Log.getStackTraceString(e));
			throw e;
		}
		catch (SocketException se) {
			Log.i(TAG,method + " " + fullUrl() + " :- SocketException: " + se.getMessage() );
			throw new SendRequestFailedException(se.getMessage());
		}
		catch (ConnectTimeoutException e)		{
			Log.i(TAG,method + " " + fullUrl() + " :- ConnectTimeoutException: " + e.getMessage() );
			throw new SendRequestFailedException(e.getMessage());
		}
		catch (Exception e) {
			Log.d(TAG,Log.getStackTraceString(e));
			if ( statusCode < 300 || statusCode > 499 )
				throw new SendRequestFailedException(e.getMessage());
		}
		return null;
	}


	
	public InputStream doRequest( String method, String path, Header[] headers, Object data ) throws SendRequestFailedException, SSLException {
		InputStream result = null;
		this.method = method;
		if ( path != null ) this.path = path;
		try {
			result = sendRequest( headers, data );
		}
		catch (SSLException e) 					{ throw e; }
		catch (SendRequestFailedException e)	{ throw e; }
		catch (Exception e) {
			Log.e(TAG,Log.getStackTraceString(e));
		}

		if ( !authRequired && statusCode == 401 ) {
			// In this case we didn't send auth credentials the first time, so
			// we need to try again after we interpret the auth request.
			try {
				interpretRequestedAuth(getAuthHeader());
				return sendRequest( headers, data );
			}
			catch (AuthenticationFailure e1) {
				throw new SendRequestFailedException("Authentication Failed: "+e1.getMessage());
			}
			catch (Exception e) {
				Log.e(TAG,Log.getStackTraceString(e));
			}
		}

		if ( (statusCode >= 300 && statusCode <= 303) || statusCode == 307 ) {
/**
 * Other than 301/302 these are all pretty unlikely
 *		300:  Multiple choices, but we take the one in the Location header anyway
 *		301:  Moved permanently
 *		302:  Found (was 'temporary redirect' once in prehistory)
 *		303:  See other
 *		307:  Temporary redirect. Meh.
 */
			String oldUrl = fullUrl();
			interpretUriString(getLocationHeader());
			if (Constants.LOG_DEBUG)
				Log.d(TAG, method + " " +oldUrl+" redirected to: "+fullUrl());
			if ( redirectCount++ < redirectLimit ) {
				// Follow redirect
				return doRequest( method, null, headers, data ); 
			}
		}

		return result;
	}

	
	/**
	 * <p>
	 * Does an XML request against the collection path
	 * </p>
	 * 
	 * @return <p>
	 *         A DavNode which is the root of the multistatus response.
	 *         </p>
	 */
	public DavNode doXmlRequest( String method, String requestPath, Header[] headers, String xml) {
		long start = System.currentTimeMillis();

		if ( Constants.LOG_DEBUG )
			Log.d(TAG, String.format("%s XML request on %s", method, fullUrl()) );

		DavNode root = null;
		try {
			root = DavXmlTreeBuilder.buildTreeFromXml( doRequest(method, requestPath, headers, xml) );
		}
		catch (Exception e) {
			Log.d(TAG, e.getMessage());
			if ( Constants.LOG_DEBUG ) Log.d(TAG, Log.getStackTraceString(e));
			return null;
		}
		
		if (Constants.LOG_VERBOSE)
			Log.v(TAG, "Request and parse completed in " + (System.currentTimeMillis() - start) + "ms");
		return root;
	}

	
}