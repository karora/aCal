/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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

package org.davical.acal;

import android.os.Environment;
import android.util.Log;

import org.davical.acal.xml.DavParserFactory;
import org.davical.acal.xml.DavParserFactory.PARSEMETHOD;

import java.util.regex.Pattern;

/**
 * Constants class for keeping Global constant values.
 *
 * @author Morphoss Ltd
 *
 */
@SuppressWarnings("unused")
public class Constants {

    public static final String PUBLIC_DATA_DIR = Environment.getExternalStorageDirectory() + "/acal/";
    public static final String COPY_DB_TARGET = PUBLIC_DATA_DIR + "acal.db"; //File path and name of copy
    public static final long MAXIMUM_SERVICE_WORKER_DELAY_MS = 1000 * 60 * 60 * 24;    //maximum time between worker thread runs in ms
    public static final long SERVICE_WORKER_GRACE_PERIOD = 1000 * 60 * 60 * 1;        //Amount of time we will allow worker to be 'late' before assuming its hung

    /**
     * Notification channel ID for calendar alarms (Android O+)
     */
    public static final String ALARM_NOTIFICATION_CHANNEL_ID = "acal_alarms";
    public static final String PRE_ALARM_NOTIFICATION_CHANNEL_ID = "acal_alarms_pre";
    public static final String ACTIVE_NOTIFICATION_CHANNEL_ID    = "acal_alarms_active";

    /**
     * Notification channel and intent extras for certificate pinning
     */
    public static final String CERT_PIN_NOTIFICATION_CHANNEL_ID = "acal_cert_pin";

    public static final String CERT_EXTRA_HOSTNAME        = "cert_hostname";
    public static final String CERT_EXTRA_PORT            = "cert_port";
    public static final String CERT_EXTRA_SUBJECT         = "cert_subject";
    public static final String CERT_EXTRA_FINGERPRINT     = "cert_fingerprint";
    public static final String CERT_EXTRA_EXPIRY          = "cert_expiry";
    public static final String CERT_EXTRA_OLD_FINGERPRINT = "cert_old_fingerprint";

    public static final String ALARM_ACTION_FIRE    = "org.davical.acal.ALARM_FIRE";
    public static final String ALARM_ACTION_PRE     = "org.davical.acal.ALARM_PRE";
    public static final String ALARM_ACTION_DISMISS = "org.davical.acal.ALARM_DISMISS";
    public static final String ALARM_ACTION_SNOOZE  = "org.davical.acal.ALARM_SNOOZE";

    public static final String ALARM_EXTRA_ROW_ID   = "alarm_row_id";
    public static final String ALARM_EXTRA_TITLE    = "alarm_title";
    public static final String ALARM_EXTRA_BASE_TTF = "alarm_base_ttf";
    public static final String ALARM_EXTRA_TTF      = "alarm_ttf";
    public static final String ALARM_EXTRA_RID      = "alarm_rid";
    public static final String ALARM_EXTRA_RRID     = "alarm_rrid";
    public static final String ALARM_EXTRA_STATE    = "alarm_state";
    public static final String ALARM_EXTRA_BLOB     = "alarm_blob";

    public static final int PRE_ALARM_OFFSET_MS = 5 * 60 * 1000;   // 5 minutes

    /**
     * Generally useful patterns
     */
    public static final Pattern lineSplitter = Pattern.compile("\\r?\\n");
    public static final Pattern rfc5545UnWrapper = Pattern.compile("\r?\n ", Pattern.DOTALL);
    public final static Pattern splitOnCommas = Pattern.compile(",");
    public static final Pattern matchSegmentName = Pattern.compile("([^/]+)$");
    public final static Pattern matchProtocolServerPort = Pattern.compile("^https?://[^/]*");

    /**
     * Set this to false and all debug logging is turned off, everywhere
     */
    public static boolean DEBUG_MODE = false;

    /**
     * How much stuff to spit out into the logs
     */
    public static boolean LOG_VERBOSE = false && DEBUG_MODE;        //Very verbose play by play execution information
    public static boolean LOG_DEBUG = false && DEBUG_MODE;            //Information relevant to debugging tasks.
    public static boolean DEBUG_SETTINGS = false && DEBUG_MODE;    // Does the debugging menu appear in Settings

    /**
     * Since Andrew's device won't display logs at DEBUG level he needs a way to fake that!* /
     * public static final int LOGV = Log.INFO;  // Normally should be Log.VERBOSE of course.
     * public static final int LOGD = Log.INFO;  // Normally should be Log.DEBUG of course.
     * public static final int LOGI = Log.WARN;  // Normally should be Log.INFO of course.
     * public static final int LOGW = Log.ERROR;  // Normally should be Log.WARN of course.
     * public static final int LOGE = Log.ASSERT; // Normally should be Log.ERROR of course.
     * /
     */
    public static final int LOGV = Log.VERBOSE;
    public static final int LOGD = Log.DEBUG;
    public static final int LOGI = Log.INFO;
    public static final int LOGW = Log.WARN;
    public static final int LOGE = Log.ERROR;
    /*/
        /** And sometimes we want to really deeply debug specific bits */
    public static final boolean debugRepeatRule = false && DEBUG_MODE;
    public static final boolean debugCalendar = false && DEBUG_MODE;
    public static final boolean debugSyncChangesToServer = false && DEBUG_MODE;
    public static final boolean debugSyncCollectionContents = false && DEBUG_MODE;
    public static final boolean debugMonthView = false && DEBUG_MODE;
    public static final boolean debugWeekView = false && DEBUG_MODE;
    public static final boolean debugEvents = false && DEBUG_MODE;
    public static final boolean debugVComponent = false && DEBUG_MODE;
    public static final boolean debugDateTime = false && DEBUG_MODE;
    public static boolean debugDavCommunication = false && DEBUG_MODE;
    public static final boolean debugAlarms = false && DEBUG_MODE;
    public static final boolean debugHeap = false && DEBUG_MODE;
    public static final boolean debugCheckServerDialog = false && DEBUG_MODE;
    public static final boolean debugTheming = false && DEBUG_MODE;
    public static final boolean debugDatabaseManager = false && DEBUG_MODE;
    public static final boolean debugWidget = false && DEBUG_MODE;
    public static final boolean debugSaxParser = false && DEBUG_MODE;
    public static final boolean debugTimeZone = false && DEBUG_MODE;

    public static boolean DISABLE_FEATURE_WEBDAV_SYNC = false && DEBUG_MODE;
    public static boolean DISABLE_FEATURE_CURRENT_PRINCIPAL = false && DEBUG_MODE;
    public static boolean DISABLE_FEATURE_TZSERVER_SUPPORT = true && DEBUG_MODE;

    public static final long DEFAULT_MAX_AGE_WIFI = 1000 * 60 * 30;        // The default to use when initialising a new collection
    public static final long DEFAULT_MAX_AGE_3G = 1000 * 60 * 60 * 2;        // The default to use when initialising a new collection

    public static final String NS_DAV = "DAV:";
    public static final String NS_CALDAV = "urn:ietf:params:xml:ns:caldav";
    public static final String NS_CARDDAV = "urn:ietf:params:xml:ns:carddav";
    public static final String NS_ACAL = "urn:org.davical.acal";
    public static final String NS_ACALCONFIG = "urn:org.davical.acalconfig";
    public static final String NS_ICAL = "http://apple.com/ns/ical/";

    public static final String CRLF = "\r\n";

    public static final String URLEncoding = "utf-8";

    public static final PARSEMETHOD XMLParseMethod = DavParserFactory.PARSEMETHOD.SAX;

}
