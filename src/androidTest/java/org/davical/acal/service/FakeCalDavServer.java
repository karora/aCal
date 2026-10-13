package org.davical.acal.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Base64;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

import org.davical.acal.service.connector.EasyX509TrustManager;

/**
 * One calendar on a CalDAV server, on this device, for aCal to synchronise
 * with.  It keeps resources with etags, a ctag, and a sync-token with the
 * changes since each one, and answers the requests aCal makes of a real
 * server.  A test changes what is on it, and reads what was asked of it.
 *
 * It is served over TLS with a certificate of its own, which the test has
 * accepted for aCal as a user would: aCal will not talk to a server in clear.
 */
public final class FakeCalDavServer {

	public static final String HOST = "localhost";
	public static final String USERNAME = "someone";
	public static final String PASSWORD = "secret";
	/** Where DAV starts on this server, which is what /.well-known/caldav leads to. */
	public static final String DAV_ROOT = "/dav/";
	public static final String PRINCIPAL = "/dav/principals/someone/";
	/** Where the user's calendars are: the calendar home set. */
	public static final String HOME = "/dav/calendars/someone/";
	public static final String CALENDAR = "/dav/calendars/someone/calendar/";

	/** One request aCal made. */
	public static final class Asked {
		public final String method;
		public final String path;
		public final String body;
		public final Map<String, String> headers;

		Asked( String method, String path, String body, Map<String, String> headers ) {
			this.method = method;
			this.path = path;
			this.body = body;
			this.headers = headers;
		}

		public String header( String name ) {
			return headers.get(name.toLowerCase());
		}

		@Override
		public String toString() {
			return method + " " + path;
		}
	}

	private static final class Stored {
		final String etag;
		final String data;

		Stored( String etag, String data ) {
			this.etag = etag;
			this.data = data;
		}
	}

	private final MockWebServer server = new MockWebServer();
	private final Map<String, Stored> resources = new LinkedHashMap<String, Stored>();
	/** The name changed at each version: the sync-token for version n is "token-n". */
	private final List<String> changes = new ArrayList<String>();
	private final List<Asked> asked = Collections.synchronizedList(new ArrayList<Asked>());
	private int etagCounter = 0;
	private boolean forgetsTokens = false;
	private String refuseMethod = null;
	private int refuseStatus = 0;
	private boolean refuseOnce = false;
	private boolean gone = false;
	private String vanishing = null;
	private boolean isDav = true;
	/** Other calendars in the home set, which are listed and nothing more: path to display name. */
	private final Map<String, String> otherCalendars = new LinkedHashMap<String, String>();

	public void start( Context context ) throws IOException {
		HeldCertificate certificate = new HeldCertificate.Builder()
				.commonName(HOST).addSubjectAlternativeName(HOST).build();
		server.useHttps(new HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory());
		server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch( RecordedRequest request ) {
				try {
					return answer(request);
				}
				catch( RuntimeException e ) {
					return new MockResponse.Builder().code(500).body(e.toString()).build();
				}
			}
		});
		server.start();
		// As a user does who is shown a certificate no authority vouches for
		EasyX509TrustManager.unpinHost(context, HOST, port());
	}

	public void stop() {
		server.close();
	}

	public int port() {
		return server.getPort();
	}

	// ---- What a test does to the server -------------------------------

	/** Put a resource on the server, as another client would. @return its etag */
	public synchronized String put( String name, String data ) {
		String etag = "\"etag-" + (++etagCounter) + "\"";
		resources.put(name, new Stored(etag, data));
		changes.add(name);
		return etag;
	}

	/** Remove a resource from the server, as another client would. */
	public synchronized void delete( String name ) {
		if ( resources.remove(name) != null ) changes.add(name);
	}

	public synchronized String data( String name ) {
		Stored stored = resources.get(name);
		return stored == null ? null : stored.data;
	}

	public synchronized String etag( String name ) {
		Stored stored = resources.get(name);
		return stored == null ? null : stored.etag;
	}

	public synchronized List<String> names() {
		return new ArrayList<String>(resources.keySet());
	}

	public synchronized String syncToken() {
		return "token-" + changes.size();
	}

	/** Every sync-token handed out so far is refused from now on, as when a server prunes its history. */
	public synchronized void forgetSyncTokens() {
		forgetsTokens = true;
	}

	/** Answer requests of this method with this status until told otherwise. */
	public synchronized void refuse( String method, int status ) {
		refuseMethod = method;
		refuseStatus = status;
		refuseOnce = false;
	}

	/** Answer the next request of this method with this status, and then go back to normal. */
	public synchronized void refuseOnce( String method, int status ) {
		refuseMethod = method;
		refuseStatus = status;
		refuseOnce = true;
	}

	public synchronized void stopRefusing() {
		refuseMethod = null;
	}

	/** The calendar is no longer on the server. */
	public synchronized void removeCalendar() {
		gone = true;
	}

	/** This resource is deleted, by someone else, just before aCal next fetches what is in it. */
	public synchronized void deleteJustBeforeItIsFetched( String name ) {
		vanishing = name;
	}

	/** It is a web server and nothing more. */
	public synchronized void isNotADavServer() {
		isDav = false;
	}

	/** Another calendar appears beside the first, as when one is made in another client. */
	public synchronized void addCalendar( String path, String displayName ) {
		otherCalendars.put(path, displayName);
	}

	public synchronized void removeOtherCalendar( String path ) {
		otherCalendars.remove(path);
	}

	public String url( String path ) {
		return "https://" + HOST + ":" + port() + path;
	}

	// ---- What a test reads of what was asked --------------------------

	public List<Asked> asked() {
		synchronized( asked ) {
			return new ArrayList<Asked>(asked);
		}
	}

	public List<Asked> asked( String method ) {
		List<Asked> matching = new ArrayList<Asked>();
		for( Asked each : asked() ) {
			if ( each.method.equals(method) ) matching.add(each);
		}
		return matching;
	}

	public void forgetWhatWasAsked() {
		asked.clear();
	}

	// ---- Answering ----------------------------------------------------

	private synchronized MockResponse answer( RecordedRequest request ) {
		String method = request.getMethod();
		String path = request.getUrl().encodedPath();
		String body = request.getBody() == null ? "" : request.getBody().utf8();
		Map<String, String> headers = new LinkedHashMap<String, String>();
		for( String name : request.getHeaders().names() ) {
			headers.put(name.toLowerCase(), request.getHeaders().get(name));
		}

		if ( !isDav ) {
			asked.add(new Asked(method, path, body, headers));
			if ( method.equals("OPTIONS") || method.equals("GET") )
				return new MockResponse.Builder().code(200).addHeader("Allow", "OPTIONS, GET, HEAD").build();
			return new MockResponse.Builder().code(405).build();
		}
		if ( path.equals("/.well-known/caldav") ) {
			return new MockResponse.Builder().code(301).addHeader("Location", DAV_ROOT).build();
		}

		String expected = "Basic " + Base64.encodeToString((USERNAME + ":" + PASSWORD).getBytes(), Base64.NO_WRAP);
		if ( !expected.equals(headers.get("authorization")) ) {
			return new MockResponse.Builder().code(401)
					.addHeader("WWW-Authenticate", "Basic realm=\"test\"").build();
		}

		asked.add(new Asked(method, path, body, headers));

		if ( method.equals(refuseMethod) ) {
			if ( refuseOnce ) refuseMethod = null;
			return new MockResponse.Builder().code(refuseStatus).build();
		}
		if ( method.equals("PROPFIND") && body.contains("current-user-principal") ) return whoAmI(path);
		if ( method.equals("PROPFIND") && path.equals(PRINCIPAL) && body.contains("calendar-home-set") ) {
			return multistatus("<response><href>" + PRINCIPAL + "</href><propstat><prop>"
					+ "<C:calendar-home-set><href>" + HOME + "</href></C:calendar-home-set>"
					+ "</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n");
		}
		if ( path.equals(HOME) && method.equals("PROPFIND") ) return listCalendars();
		if ( gone && path.startsWith(CALENDAR) ) return new MockResponse.Builder().code(404).build();

		if ( path.equals(CALENDAR) ) {
			if ( method.equals("PROPFIND") ) return propfind("1".equals(headers.get("depth")));
			if ( method.equals("REPORT") && body.contains("sync-collection") ) return syncCollection(body);
			if ( method.equals("REPORT") && body.contains("calendar-multiget") ) return multiget(body);
			if ( method.equals("OPTIONS") ) return options();
			return new MockResponse.Builder().code(405).build();
		}
		if ( path.startsWith(CALENDAR) ) {
			String name = path.substring(CALENDAR.length());
			if ( method.equals("GET") ) return get(name);
			if ( method.equals("PUT") ) return putFromClient(name, body, headers);
			if ( method.equals("DELETE") ) return deleteFromClient(name, headers);
			return new MockResponse.Builder().code(405).build();
		}
		if ( method.equals("OPTIONS") ) {
			// Outside where DAV starts it is an ordinary web server
			if ( path.startsWith(DAV_ROOT) ) return options();
			return new MockResponse.Builder().code(200).addHeader("Allow", "OPTIONS, GET, HEAD").build();
		}
		return new MockResponse.Builder().code(404).build();
	}

	/** Any DAV resource says who is asking; the principal says that it is one. */
	private static MockResponse whoAmI( String path ) {
		if ( !path.startsWith(DAV_ROOT) ) return new MockResponse.Builder().code(404).build();
		return multistatus("<response><href>" + path + "</href><propstat><prop>"
				+ "<resourcetype><collection/>" + (path.equals(PRINCIPAL) ? "<principal/>" : "") + "</resourcetype>"
				+ "<current-user-principal><href>" + PRINCIPAL + "</href></current-user-principal>"
				+ "</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n");
	}

	private static MockResponse options() {
		return new MockResponse.Builder().code(200)
				.addHeader("DAV", "1, 3, calendar-access, sync-collection")
				.addHeader("Allow", "OPTIONS, GET, PUT, DELETE, PROPFIND, REPORT").build();
	}

	private static MockResponse multistatus( String responses ) {
		return new MockResponse.Builder().code(207)
				.addHeader("Content-Type", "text/xml; charset=utf-8")
				.body("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
						+ "<multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\""
						+ " xmlns:CS=\"http://calendarserver.org/ns/\">\n" + responses + "</multistatus>\n")
				.build();
	}

	private static String xml( String text ) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private String etagResponse( String name ) {
		return "<response><href>" + CALENDAR + name + "</href><propstat><prop>"
				+ "<getetag>" + xml(resources.get(name).etag) + "</getetag>"
				+ "</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n";
	}

	private static String calendarResponse( String path, String displayName, String resourceTypes ) {
		return "<response><href>" + path + "</href><propstat><prop>"
				+ "<displayname>" + xml(displayName) + "</displayname>"
				+ "<resourcetype><collection/>" + resourceTypes + "</resourcetype>"
				+ "<supported-report-set>"
				+ "<supported-report><report><sync-collection/></report></supported-report>"
				+ "<supported-report><report><C:calendar-multiget/></report></supported-report>"
				+ "</supported-report-set>"
				+ "<current-user-privilege-set><privilege><read/></privilege><privilege><write/></privilege></current-user-privilege-set>"
				+ "<C:supported-calendar-component-set><C:comp name=\"VEVENT\"/><C:comp name=\"VTODO\"/></C:supported-calendar-component-set>"
				+ "<ICAL:calendar-color xmlns:ICAL=\"http://apple.com/ns/ical/\">#123456FF</ICAL:calendar-color>"
				+ "</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n";
	}

	/** What is in the home set: itself, the calendars, and a principal that only looks like one. */
	private MockResponse listCalendars() {
		StringBuilder responses = new StringBuilder();
		responses.append("<response><href>").append(HOME).append("</href><propstat><prop>")
				.append("<displayname>Someone</displayname><resourcetype><collection/></resourcetype>")
				.append("</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n");
		if ( !gone ) responses.append(calendarResponse(CALENDAR, "Calendar", "<C:calendar/>"));
		for( Map.Entry<String, String> other : otherCalendars.entrySet() ) {
			responses.append(calendarResponse(other.getKey(), other.getValue(), "<C:calendar/>"));
		}
		responses.append(calendarResponse(HOME + "calendar-proxy-read/", "Read access for others",
				"<principal/><calendar-proxy-read xmlns=\"http://calendarserver.org/ns/\"/><C:calendar/>"));
		return multistatus(responses.toString());
	}

	private MockResponse propfind( boolean withResources ) {
		StringBuilder responses = new StringBuilder();
		responses.append("<response><href>").append(CALENDAR).append("</href><propstat><prop>")
				.append("<CS:getctag>\"ctag-").append(changes.size()).append("\"</CS:getctag>")
				.append("<sync-token>").append(syncToken()).append("</sync-token>")
				.append("</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n");
		if ( withResources ) {
			for( String name : resources.keySet() ) {
				responses.append(etagResponse(name));
			}
		}
		return multistatus(responses.toString());
	}

	private MockResponse syncCollection( String body ) {
		int since = 0;
		Matcher token = Pattern.compile("<sync-token>([^<]*)</sync-token>").matcher(body);
		if ( token.find() ) {
			Matcher version = Pattern.compile("^token-(\\d+)$").matcher(token.group(1));
			if ( forgetsTokens || !version.matches() || Integer.parseInt(version.group(1)) > changes.size() ) {
				forgetsTokens = false;
				return new MockResponse.Builder().code(403)
						.addHeader("Content-Type", "text/xml; charset=utf-8")
						.body("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
								+ "<error xmlns=\"DAV:\"><valid-sync-token/></error>\n")
						.build();
			}
			since = Integer.parseInt(version.group(1));
		}
		StringBuilder responses = new StringBuilder();
		List<String> told = new ArrayList<String>();
		for( String name : changes.subList(since, changes.size()) ) {
			if ( told.contains(name) ) continue;
			told.add(name);
			if ( resources.containsKey(name) ) responses.append(etagResponse(name));
			else if ( since > 0 ) {
				responses.append("<response><href>").append(CALENDAR).append(name)
						.append("</href><status>HTTP/1.1 404 Not Found</status></response>\n");
			}
		}
		responses.append("<sync-token>").append(syncToken()).append("</sync-token>\n");
		return multistatus(responses.toString());
	}

	private MockResponse multiget( String body ) {
		if ( vanishing != null && body.contains(vanishing) ) {
			delete(vanishing);
			vanishing = null;
		}
		StringBuilder responses = new StringBuilder();
		Matcher href = Pattern.compile("<D:href>([^<]*)</D:href>").matcher(body);
		while( href.find() ) {
			String path = href.group(1);
			String name = path.startsWith(CALENDAR) ? path.substring(CALENDAR.length()) : path;
			Stored stored = resources.get(name);
			if ( stored == null ) {
				responses.append("<response><href>").append(xml(path))
						.append("</href><status>HTTP/1.1 404 Not Found</status></response>\n");
				continue;
			}
			responses.append("<response><href>").append(CALENDAR).append(name).append("</href><propstat><prop>")
					.append("<getetag>").append(xml(stored.etag)).append("</getetag>")
					.append("<getcontenttype>text/calendar; charset=utf-8</getcontenttype>")
					.append("<C:calendar-data>").append(xml(stored.data)).append("</C:calendar-data>")
					.append("</prop><status>HTTP/1.1 200 OK</status></propstat></response>\n");
		}
		return multistatus(responses.toString());
	}

	private MockResponse get( String name ) {
		Stored stored = resources.get(name);
		if ( stored == null ) return new MockResponse.Builder().code(404).build();
		return new MockResponse.Builder().code(200)
				.addHeader("Content-Type", "text/calendar; charset=utf-8")
				.addHeader("ETag", stored.etag).body(stored.data).build();
	}

	private MockResponse putFromClient( String name, String body, Map<String, String> headers ) {
		Stored stored = resources.get(name);
		String ifMatch = headers.get("if-match");
		if ( "*".equals(headers.get("if-none-match")) && stored != null )
			return new MockResponse.Builder().code(412).build();
		if ( ifMatch != null && (stored == null || !ifMatch.equals(stored.etag)) )
			return new MockResponse.Builder().code(412).build();
		String etag = put(name, body);
		return new MockResponse.Builder().code(stored == null ? 201 : 204).addHeader("ETag", etag).build();
	}

	private MockResponse deleteFromClient( String name, Map<String, String> headers ) {
		Stored stored = resources.get(name);
		if ( stored == null ) return new MockResponse.Builder().code(404).build();
		String ifMatch = headers.get("if-match");
		if ( ifMatch != null && !ifMatch.equals(stored.etag) )
			return new MockResponse.Builder().code(412).build();
		delete(name);
		return new MockResponse.Builder().code(204).build();
	}
}
