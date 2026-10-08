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

package org.davical.acal.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.davical.acal.Constants;
import org.davical.acal.R;
import org.davical.acal.aCal;
import org.davical.acal.providers.Servers;
import org.davical.acal.security.CredentialManager;
import org.davical.acal.security.DatabaseKeyManager;

/**
 * <p>
 * This class is responsible for maintaining, creating and upgrading our
 * database. MUST be used by any other class that needs to access the
 * database directly.
 * </p>
 *
 * @author Morphoss Ltd
 *
 */
public class AcalDBHelper extends SQLiteOpenHelper {

	public static final String TAG = "AcalDBHelper";

	// Loaded here rather than in AcalApplication because the content providers
	// open the database before Application.onCreate() runs.
	static {
		System.loadLibrary("sqlcipher");
	}

	/**
	 * Where an encrypted copy of an existing database is built, before it
	 * replaces the original.
	 */
	private static final String ENCRYPTING_DB_NAME = "acal-encrypting.db";

	/** Where an unencrypted copy is written on its way to being saved by the user. */
	private static final String PLAIN_COPY_DB_NAME = "acal-export.db";

	/** Every plain SQLite database starts with these 16 bytes; an encrypted one never does. */
	private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

	/** For opening a database that is to be copied into another file: see exportTo(). */
	private static final int EXPORT_OPEN_FLAGS = SQLiteDatabase.OPEN_READWRITE
			| SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS;

	/** The database file itself, and the files SQLite may keep beside it. */
	private static final String[] DATABASE_FILE_SUFFIXES = { "", "-journal", "-wal", "-shm" };

	private static final String LOCK_FILE_NAME = "acal_db.lock";

	private static final Object prepareLock = new Object();
	// Held for the life of the process: closing the file would drop its locks.
	private static RandomAccessFile lockFile;
	private static FileChannel lockChannel;
	private static FileLock processLock;
	private static final Object createLock = new Object();
	private static final ArrayList<Runnable> readyCallbacks = new ArrayList<Runnable>();
	private static volatile boolean ready = false;
	private static boolean preparationAttempted = false;

	/** Set by prepare(); null while the database is plain SQLite. */
	private static volatile byte[] databaseKey = null;

	/**
	 * The name of the database, which will be stored in:
	 *    /data/data/org.davical.acal/databases/[DB_NAME].db
	 */
	public static final String DB_NAME = "acal";

	/**
	 * The version of this database. Used to determine if an upgrade is required.
	 */
	public static final int DB_VERSION = 22;



	/**
	 * <p>The dav_server Table as stated in the specification.</p>
	 */
	public static final String DAV_SERVER_TABLE_SQL =
			"CREATE TABLE dav_server ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",friendly_name TEXT"
				+",supplied_user_url TEXT"
				+",supplied_path TEXT"
				+",use_ssl BOOLEAN"
				+",hostname TEXT"
				+",port INTEGER"
				+",principal_path TEXT"
				+",auth_type INTEGER"
				+",username TEXT"
				+",password TEXT"
				+",has_srv BOOLEAN"
				+",has_wellknown BOOLEAN"
				+",has_caldav BOOLEAN"
				+",has_multiget BOOLEAN"
				+",has_sync BOOLEAN"
				+",active BOOLEAN"
				+",last_checked DATETIME"
				+",use_advanced BOOLEAN"
				+",prepared_config TEXT"
				+",UNIQUE(use_ssl,hostname,port,principal_path,username)"
			+")";

	/**
	 * <p>The dav_path_set table holds the paths which are collections containing
	 * the collections we are <em>really</em> interested in.  We use this for
	 * holding the responses to calendar-home-set, addressbook-home-set and
	 * principal-collection-set properties retrieved from the server.</p>
	 */
	public static final String DAV_PATH_SET_TABLE_SQL =
			"CREATE TABLE dav_path_set ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",server_id INTEGER REFERENCES dav_server(_id)"
				+",set_type INT"
				+",path TEXT"
				+",collection_tag TEXT"
				+",last_checked DATETIME"
				+",needs_sync BOOLEAN"
				+",UNIQUE(server_id, set_type, path)"
			+");";

	/**
	 * <p>The dav_collection holds information about the collections which we
	 * synchronise with the server.</p>
	 */
	public static final String DAV_COLLECTION_TABLE_SQL =
			"CREATE TABLE dav_collection ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",server_id INTEGER REFERENCES dav_server(_id)"
				+",collection_path TEXT"
				+",displayname TEXT"
				+",holds_events BOOLEAN"
				+",holds_tasks BOOLEAN"
				+",holds_journal BOOLEAN"
				+",holds_addressbook BOOLEAN"
				+",active_events BOOLEAN"
				+",active_tasks BOOLEAN"
				+",active_journal BOOLEAN"
				+",active_addressbook BOOLEAN"
				+",last_synchronised DATETIME"
				+",needs_sync BOOLEAN"
				+",sync_token TEXT"
				+",collection_tag TEXT"
				+",default_timezone TEXT"
				+",colour TEXT"
				+",use_alarms BOOLEAN"
				+",max_sync_age_wifi INTEGER"
				+",max_sync_age_3g INTEGER"
				+",is_writable BOOLEAN"
				+",is_visible BOOLEAN"
				+",sync_metadata BOOLEAN"
				+",manually_added BOOLEAN"
				+",UNIQUE(server_id,collection_path)"
			+");";

	/**
	 * <p>The dav_resource stores the resources (vevents, vtodos, vjournals & vcards)</p>
	 */
	public static final String DAV_RESOURCE_TABLE_SQL =
			"CREATE TABLE dav_resource ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			  	+",collection_id INTEGER REFERENCES dav_collection(_id)"
			  	+",name TEXT"
			  	+",etag TEXT"
			  	+",last_modified DATETIME"
			  	+",content_type TEXT"
			  	+",data BLOB"
			  	+",needs_sync BOOLEAN"
			  	+",earliest_start NUMERIC"
			  	+",latest_end NUMERIC"
			  	+",effective_type TEXT"
			  	+",UNIQUE(collection_id,name)"
			+");";


	/**
	 * <p>Some indexes</p>
	 */
	public static final String EVENT_INDEX_SQL =
		"CREATE UNIQUE INDEX event_select_idx ON dav_resource ( effective_type, collection_id, latest_end, _id );";
	public static final String TODO_INDEX_SQL =
		"CREATE UNIQUE INDEX todo_select_idx ON dav_resource ( effective_type, collection_id, _id );";


	/**
	 * The pending_change, containing the fully constructed resource we want
	 * to PUT to the server when we can.
	 *
	 * In the event of a local CREATE pending the 'old_data' blob will be NULL.
	 * In the event of a local DELETE pending the 'new_data' blob will be NULL.
	 *
	 * The SHOULD only be one pending_change active for a resource, and multiple
	 * changes SHOULD be merged where that is possible (i.e. where the status
	 * does not indicate it has already been submitted to the server and we are
	 * merely waiting to see it back again...
	 */
	public static final String PENDING_CHANGE_TABLE_SQL =
			"CREATE TABLE pending_change ("
		        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",collection_id INTEGER REFERENCES dav_collection(_id)"
				+",resource_id INTEGER REFERENCES dav_resource(_id)"
				+",old_data BLOB"
				+",new_data BLOB"
				+",uid TEXT"
				+",UNIQUE(collection_id,resource_id)"
			+");";


	/**
	 * A Table for storing data pertinent to the Show Upcoming Widget.
	 * Introduced into version 13.
	 */
	public static final String SHOW_UPCOMING_WIDGET_TABLE_SQL =
		"CREATE TABLE show_upcoming_widget_data ("
	        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			+",resource_id INTEGER REFERENCES dav_resource(_id)"
			+",etag TEXT"
			+",colour INTEGER"
			+",dtstart NUMERIC"
			+",dtend NUMERIC"
			+",summary TEXT"
		+");";


	/**
	 * Used for caching event data
	 */
	public static final String RESOURCE_CACHE_TABLE_SQL =
		"CREATE TABLE event_cache ("
	        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			+",resource_id INTEGER REFERENCES dav_resource(_id)"
			+",resource_type TEXT"
			+",recurrence_id TEXT"
			+",collection_id NUMERIC"
			+",summary TEXT"
			+",location TEXT"
			+",dtstart NUMERIC"
			+",dtend NUMERIC"
			+",completed BOOLEAN"
			+",dtstartfloat BOOLEAN"
			+",dtendfloat BOOLEAN"
			+",completedfloat BOOLEAN"
			+",flags INTEGER"
		+");";

	public static final String RESOURCE_CACHE_META_TABLE_SQL =
		"CREATE TABLE event_cache_meta ("
	        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			+",dtstart NUMERIC"
			+",dtend NUMERIC"
			+",count INTEGER"
			+",closed BOOLEAN"
		+");";

	private static final long now = System.currentTimeMillis();
	public static final String SET_RESOURCE_CACHE_DIRTY_SQL =
			"INSERT INTO event_cache_meta (dtstart, dtend, count, closed) VALUES("+now+","+now+",0,0)";

	public static final String ALARM_TABLE_SQL =
		"CREATE TABLE alarms ("
	        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			+",ttf NUMERIC"
	        +",base_ttf NUMERIC"
			+",rid NUMERIC"
			+",rrid TEXT"
			+",state NUMERIC"
			+", blob TEXT"
		+");";

	public static final String ALARM_META_TABLE_SQL =
		"CREATE TABLE alarm_meta ("
	        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
			+",closed BOOLEAN"
		+");";
	public static final String CLEAR_ALARM_META_TABLE_SQL = "DELETE FROM alarm_meta";
	public static final String SET_ALARM_TABLE_DIRTY_SQL =
			"INSERT INTO alarm_meta (closed) VALUES(0)";

	public static final String TIMEZONE_TABLE_SQL =
			"CREATE TABLE timezone ("
		        +"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",tzid TEXT"
				+",default_name TEXT"
		        +",last_modified NUMERIC"
		        +",zone_data BLOB"
		        +", UNIQUE(tzid)"
			+");";

	public static final String TIMEZONE_NAME_TABLE_SQL =
			"CREATE TABLE timezone_name ("
				+"_id INTEGER PRIMARY KEY AUTOINCREMENT"
				+",tzid TEXT REFERENCES timezone(tzid)"
				+",tzname TEXT"
				+",locale TEXT"
				+",UNIQUE(tzid,locale)"
			+");";

	public static final String TIMEZONE_ALIAS_TABLE_SQL =
			"CREATE TABLE timezone_alias ("
				+"alias TEXT PRIMARY KEY "
				+",tzid TEXT REFERENCES timezone(tzid)"
			+");";

	private final Context	context;

	/**
	 * Visible single argument constructor. Calls super with default values.
	 *
	 * @param context The context in which this DB will be used.
	 * @author Morphoss Ltd
	 */
	public AcalDBHelper (Context context) {
		super (context, DB_NAME+".db", null, DB_VERSION);
		this.context = context;
	}

	/**
	 * <p>
	 * Called when database is first instantiated. Creates default schema.
	 * </p>
	 *
	 * @see SQLiteOpenHelper#onCreate(SQLiteDatabase)
	 * @author Morphoss Ltd
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create Database:
		db.execSQL(DAV_SERVER_TABLE_SQL);
		createMostTables(db,false);
	}

	/**
	 * <p>
	 * Executed if exiting DB version does not match the one defined by DB_VERSION.
	 * Currently drops all but the dav_server table and recreates them.  It will
	 * eventually do a rescan of the servers after that, re-discovering collections
	 * and rebuilding our local cache.
	 * </p>
	 *
	 * @see SQLiteOpenHelper#onUpgrade(SQLiteDatabase, int, int)
	 * @author Morphoss Ltd
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		Log.i(TAG,"Attempting to upgrade database from "+oldVersion+" to "+newVersion);

		// We drop tables in the reverse order to avoid constraint issues

		try {
			if ( oldVersion == 9 ) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				Log.i(TAG,"Updating database to version " + oldVersion);
			}

			if ( oldVersion == 10 ) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("ALTER TABLE dav_server ADD COLUMN use_advanced BOOLEAN");
				db.execSQL("ALTER TABLE dav_server ADD COLUMN prepared_config TEXT");
			}
			if ( oldVersion == 11 ) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("ALTER TABLE dav_resource ADD COLUMN effective_type TEXT");
				db.execSQL("UPDATE dav_resource SET effective_type = 'VCARD' WHERE lower(data) LIKE 'begin:vcard';");
				db.execSQL("UPDATE dav_resource SET effective_type = 'VEVENT' WHERE lower(data) LIKE 'begin:vevent';");
				db.execSQL("UPDATE dav_resource SET effective_type = 'VJOURNAL' WHERE lower(data) LIKE 'begin:vjournal';");
				db.execSQL("UPDATE dav_resource SET effective_type = 'VTODO' WHERE lower(data) LIKE 'begin:vtodo';");
				db.execSQL(EVENT_INDEX_SQL);
				db.execSQL(TODO_INDEX_SQL);
			}
			if ( oldVersion == 12 ) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("PRAGMA writable_schema = 1");
				db.execSQL("UPDATE SQLITE_MASTER SET SQL = '"+DAV_SERVER_TABLE_SQL+"' WHERE name = '"+Servers.DATABASE_TABLE+"'");
				db.execSQL("PRAGMA writable_schema = 0");
			}

			if ( oldVersion == 13 ) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL(SHOW_UPCOMING_WIDGET_TABLE_SQL);
			}

			if (oldVersion == 14) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("DROP TABLE show_upcoming_widget_data");
				db.execSQL(SHOW_UPCOMING_WIDGET_TABLE_SQL);
			}

			if (oldVersion == 15) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL(RESOURCE_CACHE_TABLE_SQL);
				db.execSQL(RESOURCE_CACHE_META_TABLE_SQL);
				db.execSQL(SET_RESOURCE_CACHE_DIRTY_SQL);
			}

			if (oldVersion == 16) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("DROP TABLE event_cache");
				db.execSQL(RESOURCE_CACHE_TABLE_SQL);
				db.execSQL("DELETE FROM event_cache_meta");
				db.execSQL(SET_RESOURCE_CACHE_DIRTY_SQL);
				db.execSQL("UPDATE dav_collection SET needs_sync=1, sync_token=NULL, collection_tag=NULL");
				db.execSQL("DELETE FROM dav_resource");
			}

			if (oldVersion == 17) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL("DROP TABLE pending_change");
				db.execSQL(PENDING_CHANGE_TABLE_SQL);
			}
			if (oldVersion == 18) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL(ALARM_TABLE_SQL);
				db.execSQL(ALARM_META_TABLE_SQL);
				db.execSQL(SET_ALARM_TABLE_DIRTY_SQL);
			}
			if (oldVersion == 19) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				db.execSQL(TIMEZONE_TABLE_SQL);
				db.execSQL(TIMEZONE_NAME_TABLE_SQL);
				db.execSQL(TIMEZONE_ALIAS_TABLE_SQL);
				db.execSQL("ALTER TABLE dav_collection ADD COLUMN manually_added BOOLEAN");
			}
            if (oldVersion == 19) {
                Log.i(TAG,"Updating database from version " + oldVersion);
                oldVersion++;
                db.execSQL("DROP TABLE alarms");
                db.execSQL(ALARM_TABLE_SQL);
                db.execSQL(SET_ALARM_TABLE_DIRTY_SQL);
            }
			if (oldVersion == 20) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				// Version 21 was a no-op structural change
			}
			if (oldVersion == 21) {
				Log.i(TAG,"Updating database from version " + oldVersion);
				oldVersion++;
				// Migrate plaintext passwords to encrypted storage
				migratePasswordsToEncrypted(db);
			}
		}
		catch( Exception e ) {
			Log.e(TAG,"Failed to upgrade database carefully.", e);
		}
		finally {
		}

		if ( oldVersion != newVersion ) {
			// Fallback to try and drop all tables, except the server table and
			// then recreate them.
			recoverDatabase(db,true);
		}
		else {
			Log.i(TAG,"Database now upgraded to version " + newVersion);
		}

	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
	}


	public SQLiteDatabase getReadableDatabase() {
		prepare(context);
		try {
			SQLiteDatabase db = open(context.getDatabasePath(DB_NAME+".db").toString(),
					SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			if ( db.getVersion() == DB_VERSION ) return db;
			db.close();
		}
		catch( SQLiteException e) {
			Log.i(TAG,e.getMessage());
		}
		return getWritableDatabase();
	}

	private SQLiteDatabase openWritableDatabase( String dbPath ) {
		SQLiteDatabase db = open( dbPath, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		if ( db.getVersion() == DB_VERSION ) return db;
		db.beginTransaction();
		onUpgrade(db, db.getVersion(), DB_VERSION);
		db.setVersion(DB_VERSION);
		db.setTransactionSuccessful();
		db.endTransaction();
		return db;
	}

	private SQLiteDatabase createDatabase( String dbPath ) {
		new File(dbPath).getParentFile().mkdirs();
		SQLiteDatabase db = open( dbPath, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY
				| SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		db.beginTransaction();
		try {
			// Another process may have created it while we waited for the lock.
			if ( db.getVersion() == 0 ) {
				onCreate(db);
				db.setVersion(DB_VERSION);
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
		return db;
	}

	public SQLiteDatabase getWritableDatabase() {
		prepare(context);
		String dbPath = context.getDatabasePath(DB_NAME+".db").toString();

		synchronized( createLock ) {
			// Never hand a new database to the superclass to create: it has no key.
			if ( new File(dbPath).length() == 0 ) return createDatabase(dbPath);
		}

		int attempts = 0;
		while( attempts++ < 500 ) {
			try {
				return openWritableDatabase(dbPath);
			}
			catch( SQLiteNotADatabaseException e ) {
				// The key is wrong, which no amount of retrying will fix.
				throw e;
			}
			catch( SQLiteException e) {
				Log.println(Constants.LOGD,TAG,"Unable to get writable database - retrying");
			}
			try { Thread.sleep(10); } catch (Exception e) {}
		}

		// Once more, to let the failure propagate.
		return openWritableDatabase(dbPath);
	}


	/**
	 * Open a database with the key chosen by prepare(), or as plain SQLite if
	 * there is none.
	 */
	private static SQLiteDatabase open( String dbPath, int flags ) {
		byte[] key = databaseKey;
		if ( key == null ) return SQLiteDatabase.openDatabase(dbPath, null, flags);
		return SQLiteDatabase.openDatabase(dbPath, key, null, flags, null);
	}


	/**
	 * Write a copy of the database, as plain unencrypted SQLite, to a file of
	 * our own.  The caller must hand it back to deletePlainCopy() when it has
	 * finished with it.
	 *
	 * @return The file the copy was written to.
	 */
	public static File exportPlainCopy( Context context ) {
		prepare(context);
		File target = plainCopyFile(context);
		deleteDatabaseFiles(target);
		SQLiteDatabase db = open(context.getDatabasePath(DB_NAME+".db").toString(), EXPORT_OPEN_FLAGS);
		try {
			exportTo(db, target, "", db.getVersion());
		}
		finally {
			db.close();
		}
		return target;
	}

	public static void deletePlainCopy( Context context ) {
		deleteDatabaseFiles(plainCopyFile(context));
	}

	private static File plainCopyFile( Context context ) {
		return new File(context.getCacheDir(), PLAIN_COPY_DB_NAME);
	}


	/**
	 * @return true once the database is ready to be opened without waiting,
	 * which on the first run after an upgrade means it has been encrypted.
	 */
	public static boolean isReady() {
		return ready;
	}

	/**
	 * Run something once the database has been prepared: straight away, on the
	 * calling thread, if it already has been, otherwise later on whichever
	 * thread did the preparing.
	 */
	public static void whenReady( Runnable callback ) {
		synchronized( readyCallbacks ) {
			if ( !preparationAttempted ) {
				readyCallbacks.add(callback);
				return;
			}
		}
		callback.run();
	}

	/**
	 * Prepare the database on a background thread, so that encrypting an
	 * existing database does not hold up whoever opens it first.  Anything that
	 * does open the database in the meantime waits for this to finish.
	 */
	public static void prepareInBackground( Context context ) {
		if ( ready ) return;
		final Context appContext = context.getApplicationContext();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					prepare(appContext);
				}
				catch( RuntimeException e ) {
					Log.e(TAG,"Could not prepare the database", e);
				}
			}
		}, "aCal database preparation").start();
	}

	/**
	 * Decide, once per process, how the database is to be opened.  An existing
	 * plain SQLite database is encrypted here, and everything else that wants
	 * the database waits until that is done.
	 *
	 * @throws SQLiteException if the database is encrypted and its key cannot
	 * be fetched at the moment.  That is not remembered, so the next open tries
	 * again.
	 */
	private static void prepare( Context context ) {
		if ( ready ) return;
		try {
			synchronized( prepareLock ) {
				if ( ready ) return;
				databaseKey = chooseKey(context.getApplicationContext());
				ready = true;
				Log.i(TAG, databaseKey != null ? "The database is encrypted" : "The database is NOT encrypted");
			}
		}
		finally {
			ArrayList<Runnable> callbacks;
			synchronized( readyCallbacks ) {
				preparationAttempted = true;
				callbacks = new ArrayList<Runnable>(readyCallbacks);
				readyCallbacks.clear();
			}
			for( Runnable callback : callbacks ) {
				try {
					callback.run();
				}
				catch( RuntimeException e ) {
					Log.e(TAG,"Error telling a listener that the database is ready", e);
				}
			}
		}
	}

	/**
	 * aCal runs in more than one process, and each opens the database for
	 * itself.  Anything that changes how the database is opened (encrypting it,
	 * making its key, or replacing it) would pull the rug out from under another
	 * process that already has it open, so it is only done by a process that is
	 * the only one running.
	 *
	 * That is arranged with two byte ranges of a lock file: one that is held
	 * while a process works out how to open the database, and one that each
	 * process then holds, shared, for as long as it lives.  A process that can
	 * lock the second range exclusively knows it is alone.
	 *
	 * @return The key to open the database with, or null to use plain SQLite.
	 */
	private static byte[] chooseKey( Context context ) {
		try {
			if ( lockChannel == null ) {
				lockFile = new RandomAccessFile(new File(context.getNoBackupFilesDir(), LOCK_FILE_NAME), "rw");
				lockChannel = lockFile.getChannel();
			}
			FileLock deciding = lockChannel.lock(0, 1, false);
			try {
				if ( processLock != null ) {
					// Left from an earlier attempt that failed.
					processLock.release();
					processLock = null;
				}
				FileLock alone = lockChannel.tryLock(1, 1, false);
				if ( alone == null ) {
					processLock = lockChannel.lock(1, 1, true);
					return keyChosenByAnotherProcess(context);
				}
				try {
					return chooseKeyAlone(context);
				}
				finally {
					alone.release();
					processLock = lockChannel.lock(1, 1, true);
				}
			}
			finally {
				deciding.release();
			}
		}
		catch( IOException e ) {
			throw new SQLiteException("Could not lock the database between processes: " + e);
		}
	}

	/**
	 * Another aCal process is running, so open the database the way it will
	 * have: nothing may be changed here.
	 */
	private static byte[] keyChosenByAnotherProcess( Context context ) {
		File dbFile = context.getDatabasePath(DB_NAME+".db");
		boolean exists = dbFile.length() > 0;
		if ( exists && isPlainSqlite(dbFile) ) return null;

		byte[] key;
		try {
			key = DatabaseKeyManager.getExistingKey(context);
		}
		catch( GeneralSecurityException | RuntimeException e ) {
			throw new SQLiteException("The database key is not available: " + e);
		}
		if ( key == null && exists ) throw new SQLiteException("The database is encrypted but has no key");
		return key;
	}

	private static byte[] chooseKeyAlone( Context context ) {
		File dbFile = context.getDatabasePath(DB_NAME+".db");
		File encrypting = new File(dbFile.getParentFile(), ENCRYPTING_DB_NAME);
		deleteDatabaseFiles(encrypting);
		// An unencrypted copy that was being saved when we were last killed.
		deletePlainCopy(context);

		if ( dbFile.length() == 0 ) {
			// A new database: encrypt it from the start if we can.
			try {
				return DatabaseKeyManager.getOrCreateKey(context);
			}
			catch( GeneralSecurityException | RuntimeException e ) {
				Log.e(TAG,"No database key available: the new database will not be encrypted", e);
				return null;
			}
		}

		if ( isPlainSqlite(dbFile) ) {
			try {
				byte[] key = DatabaseKeyManager.getOrCreateKey(context);
				encryptExistingDatabase(dbFile, encrypting, key);
				return key;
			}
			catch( GeneralSecurityException | RuntimeException e ) {
				// Carry on unencrypted.  The next process start will try again.
				Log.e(TAG,"Could not encrypt the database: continuing without encryption", e);
				deleteDatabaseFiles(encrypting);
				return null;
			}
		}

		byte[] key = null;
		try {
			key = DatabaseKeyManager.getExistingKey(context);
		}
		catch( DatabaseKeyManager.KeyLostException e ) {
			Log.e(TAG,"The database key has been lost", e);
		}
		catch( GeneralSecurityException | RuntimeException e ) {
			// Possibly temporary, so the database must not be thrown away.
			throw new SQLiteException("The database key is not available: " + e);
		}
		if ( key != null && opensWithKey(dbFile, key) ) return key;

		return resetUnreadableDatabase(context, dbFile);
	}

	private static boolean isPlainSqlite( File dbFile ) {
		byte[] header = new byte[SQLITE_HEADER.length];
		try ( FileInputStream in = new FileInputStream(dbFile) ) {
			int count = 0;
			while( count < header.length ) {
				int n = in.read(header, count, header.length - count);
				if ( n < 0 ) break;
				count += n;
			}
			return count == header.length && Arrays.equals(header, SQLITE_HEADER);
		}
		catch( IOException e ) {
			throw new SQLiteException("Could not read the database header: " + e);
		}
	}

	private static boolean opensWithKey( File dbFile, byte[] key ) {
		try {
			SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), key, null,
					SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS, null);
			try {
				db.getVersion();
			}
			finally {
				db.close();
			}
		}
		catch( SQLiteNotADatabaseException e ) {
			Log.e(TAG,"The database cannot be read with the stored key", e);
			return false;
		}
		catch( SQLiteException e ) {
			// Some other problem, such as a lock, which is not the key's fault.
			Log.w(TAG,"Unexpected error checking the database key", e);
		}
		return true;
	}

	/**
	 * Replace a plain SQLite database with an encrypted copy of itself.  The
	 * copy is built alongside and renamed over the original, so the database
	 * file is always either the whole original or the whole encrypted copy.
	 */
	private static void encryptExistingDatabase( File dbFile, File encrypting, byte[] key ) {
		long started = System.currentTimeMillis();
		Log.i(TAG,"Encrypting the existing database (" + dbFile.length() + " bytes)");

		int version;
		String contents;
		SQLiteDatabase plain = SQLiteDatabase.openDatabase(dbFile.getPath(), null, EXPORT_OPEN_FLAGS);
		try {
			version = plain.getVersion();
			contents = describeContents(plain);
			exportTo(plain, encrypting, new String(key, StandardCharsets.US_ASCII), version);
		}
		finally {
			plain.close();
		}

		if ( isPlainSqlite(encrypting) ) throw new SQLiteException("The encrypted copy is not encrypted");
		SQLiteDatabase copy = SQLiteDatabase.openDatabase(encrypting.getPath(), key, null,
				SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS, null);
		try {
			if ( copy.getVersion() != version || !describeContents(copy).equals(contents) )
				throw new SQLiteException("The encrypted copy does not match the original");
		}
		finally {
			copy.close();
		}

		// A journal left beside the old file must not be applied to the new one.
		for( String suffix : DATABASE_FILE_SUFFIXES ) {
			if ( !suffix.isEmpty() ) new File(dbFile.getPath() + suffix).delete();
		}
		if ( !encrypting.renameTo(dbFile) ) throw new SQLiteException("Could not move the encrypted copy into place");

		Log.i(TAG,"Database encrypted in " + (System.currentTimeMillis() - started) + "ms");
	}

	/**
	 * Copy everything in an open database into another file, which is created.
	 *
	 * Two things here were learned the hard way.  The database being copied has
	 * to have been opened with CREATE_IF_NECESSARY, because SQLite creates an
	 * attached file only if the connection it is attached to was opened that
	 * way.  And SQLiteDatabase.rawExecSQL() must not be used: it silently
	 * ignores a statement that fails when it is run.
	 *
	 * @param key The key for the new file, in the form SQLCipher expects, or an
	 * empty string to write plain SQLite.
	 */
	private static void exportTo( SQLiteDatabase db, File target, String key, int version ) {
		db.execSQL("ATTACH DATABASE ? AS export KEY ?", new Object[] { target.getPath(), key });
		try {
			Cursor c = db.rawQuery("SELECT sqlcipher_export('export')", (String[]) null);
			try {
				c.moveToFirst();
			}
			finally {
				c.close();
			}
			// The export does not carry the schema version across.
			db.execSQL("PRAGMA export.user_version = " + version);
		}
		finally {
			db.execSQL("DETACH DATABASE export");
		}
	}

	/**
	 * @return The number of things in the schema and of rows in each table, to
	 * tell whether a copy of a database has everything the original had.
	 */
	private static String describeContents( SQLiteDatabase db ) {
		ArrayList<String> tables = new ArrayList<String>();
		StringBuilder contents = new StringBuilder();
		Cursor c = db.rawQuery("SELECT type, name FROM sqlite_master ORDER BY type, name", (String[]) null);
		try {
			contents.append(c.getCount()).append(" objects");
			while( c.moveToNext() ) {
				if ( "table".equals(c.getString(0)) ) tables.add(c.getString(1));
			}
		}
		finally {
			c.close();
		}
		for( String table : tables ) {
			c = db.rawQuery("SELECT count(*) FROM \"" + table.replace("\"", "\"\"") + "\"", (String[]) null);
			try {
				c.moveToFirst();
				contents.append(", ").append(table).append('=').append(c.getLong(0));
			}
			finally {
				c.close();
			}
		}
		return contents.toString();
	}

	/**
	 * The database is encrypted with a key we no longer have, so nothing in it
	 * can be read again.  Start afresh: everything except unsynchronised changes
	 * and the server settings themselves is still on the server.
	 *
	 * @return The key for the replacement database, or null to use plain SQLite.
	 */
	private static byte[] resetUnreadableDatabase( Context context, File dbFile ) {
		Log.e(TAG,"The database cannot be decrypted and is being replaced with an empty one");
		deleteDatabaseFiles(dbFile);
		DatabaseKeyManager.discardKey(context);
		notifyDatabaseReset(context);
		try {
			return DatabaseKeyManager.getOrCreateKey(context);
		}
		catch( GeneralSecurityException | RuntimeException e ) {
			Log.e(TAG,"No database key available: the new database will not be encrypted", e);
			return null;
		}
	}

	private static void deleteDatabaseFiles( File dbFile ) {
		for( String suffix : DATABASE_FILE_SUFFIXES ) {
			new File(dbFile.getPath() + suffix).delete();
		}
	}

	private static void notifyDatabaseReset( Context context ) {
		Intent launch = new Intent(context, aCal.class);
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pi = PendingIntent.getActivity(context, Constants.DATABASE_RESET_NOTIFICATION_ID, launch,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		String text = context.getString(R.string.Database_reset_text);
		Notification.Builder builder = new Notification.Builder(context, Constants.STATUS_NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(R.drawable.icon)
				.setContentTitle(context.getString(R.string.Database_reset_title))
				.setContentText(text)
				.setStyle(new Notification.BigTextStyle().bigText(text))
				.setContentIntent(pi)
				.setAutoCancel(true);
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(Constants.DATABASE_RESET_NOTIFICATION_ID, builder.build());
	}


	public synchronized void close(SQLiteDatabase db) {
		try {
			db.close();
			int counter = 500;
			while (db.isOpen() && counter-- > 0) {
				try { Thread.sleep(50); } catch ( InterruptedException e ) { }
			}
		}
		catch( SQLiteException e ) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		super.close();
	}


	public static void createMostTables(SQLiteDatabase db, boolean keepCollections) {
		Log.i(TAG,"Creating database tables for version " + DB_VERSION);
		try {
			if ( !keepCollections ) {
				db.execSQL(DAV_PATH_SET_TABLE_SQL);
				db.execSQL(DAV_COLLECTION_TABLE_SQL);
			}
			db.execSQL(DAV_RESOURCE_TABLE_SQL);
			db.execSQL(PENDING_CHANGE_TABLE_SQL);
			db.execSQL(EVENT_INDEX_SQL);
			db.execSQL(TODO_INDEX_SQL);

			db.execSQL(RESOURCE_CACHE_TABLE_SQL);
			db.execSQL(RESOURCE_CACHE_META_TABLE_SQL);
			db.execSQL(SET_RESOURCE_CACHE_DIRTY_SQL);

			db.execSQL(SHOW_UPCOMING_WIDGET_TABLE_SQL);

			db.execSQL(ALARM_TABLE_SQL);
			db.execSQL(ALARM_META_TABLE_SQL);
			db.execSQL(SET_ALARM_TABLE_DIRTY_SQL);

			db.execSQL(TIMEZONE_TABLE_SQL);
			db.execSQL(TIMEZONE_NAME_TABLE_SQL);
			db.execSQL(TIMEZONE_ALIAS_TABLE_SQL);
		}
		catch( Exception e ) {
			Log.e(TAG, "Database error creating database tables", e);
		}
	}

	public static void recoverDatabase(SQLiteDatabase db, boolean keepCollections) {
		Log.i(TAG,"Recovering database to version " + DB_VERSION);
		try {
			// Drop all the tables except the dav_server one.
			try { db.execSQL("DROP TABLE timezone_alias"); } catch( Exception e ) {}
			try { db.execSQL("DROP TABLE timezone_name"); } catch( Exception e ) {}
			try { db.execSQL("DROP TABLE timezone"); } catch( Exception e ) {}

			try { db.execSQL("DROP TABLE alarm_meta"); } catch( Exception e ) {}
			try { db.execSQL("DROP TABLE alarms"); } catch( Exception e ) {}

			try { db.execSQL("DROP TABLE event_cache_meta"); } catch( Exception e ) {}
			try { db.execSQL("DROP TABLE event_cache"); } catch( Exception e ) {}

			try { db.execSQL("DROP TABLE show_upcoming_widget_data"); } catch( Exception e ) {}

			try { db.execSQL("DROP TABLE pending_change"); } catch( Exception e ) {}
			try { db.execSQL("DROP TABLE dav_resource"); } catch( Exception e ) {}
			if ( !keepCollections ) {
				try { db.execSQL("DROP TABLE dav_collection"); } catch( Exception e ) {}
				try { db.execSQL("DROP TABLE dav_path_set"); } catch( Exception e ) {}
			}

			// Recreate the tables we just dropped.
			createMostTables(db,keepCollections);
		}
		catch( Exception e ) {
			Log.e(TAG, "Database error recreating database", e);
		}
	}

	/**
	 * Migrate existing plaintext passwords to encrypted storage.
	 * Called during database upgrade to version 22.
	 */
	private void migratePasswordsToEncrypted(SQLiteDatabase db) {
		Log.i(TAG, "Migrating passwords to encrypted storage");
		android.database.Cursor cursor = null;
		try {
			CredentialManager cm = CredentialManager.getInstance(context);

			cursor = db.query(Servers.DATABASE_TABLE,
					new String[]{Servers._ID, Servers.PASSWORD},
					null, null, null, null, null);

			if (cursor != null && cursor.moveToFirst()) {
				do {
					long id = cursor.getLong(0);
					String password = cursor.getString(1);

					if (password != null && !password.isEmpty() && !cm.isEncrypted(password)) {
						String encrypted = cm.encrypt(password);
						if (encrypted != null) {
							android.content.ContentValues values = new android.content.ContentValues();
							values.put(Servers.PASSWORD, encrypted);
							db.update(Servers.DATABASE_TABLE, values,
									Servers._ID + " = ?", new String[]{String.valueOf(id)});
							Log.i(TAG, "Encrypted password for server ID: " + id);
						}
					}
				} while (cursor.moveToNext());
			}
			Log.i(TAG, "Password migration completed");
		} catch (Exception e) {
			Log.e(TAG, "Error migrating passwords", e);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
}
