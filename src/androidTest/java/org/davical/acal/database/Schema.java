package org.davical.acal.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import android.database.Cursor;

import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

/**
 * The statements each version of aCal made a new database with, kept in
 * assets/schema/vN.sql, and a way to say whether two databases have the same
 * tables, columns and indexes.
 */
public final class Schema {

	private Schema() { }

	/** @return the versions there is a file of statements for, lowest first */
	public static List<Integer> versions() throws IOException {
		TreeSet<Integer> versions = new TreeSet<Integer>();
		for( String name : InstrumentationRegistry.getInstrumentation().getContext().getAssets().list("schema") ) {
			if ( name.matches("v\\d+\\.sql") ) versions.add(Integer.parseInt(name.substring(1, name.length() - 4)));
		}
		return new ArrayList<Integer>(versions);
	}

	public static List<String> statements( int version ) throws IOException {
		List<String> statements = new ArrayList<String>();
		try ( BufferedReader in = new BufferedReader(new InputStreamReader(
				InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("schema/v" + version + ".sql"), "UTF-8")) ) {
			String line;
			while( (line = in.readLine()) != null ) {
				line = line.trim();
				if ( line.isEmpty() || line.startsWith("--") ) continue;
				statements.add(line.endsWith(";") ? line.substring(0, line.length() - 1) : line);
			}
		}
		return statements;
	}

	/**
	 * Make an unencrypted database as that version of aCal would have, which
	 * is how every database older than the encryption was kept.
	 */
	public static SQLiteDatabase createPlain( File file, int version ) throws IOException {
		file.getParentFile().mkdirs();
		SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null,
				SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		for( String statement : statements(version) ) {
			db.execSQL(statement);
		}
		db.setVersion(version);
		return db;
	}

	/**
	 * @return each table with its columns, and each index with what it covers,
	 * in an order that does not depend on how the database came to be.  A
	 * column added by an upgrade comes last in its table and one in a new
	 * database comes where it was written, and they are the same schema.
	 */
	public static String describe( SQLiteDatabase db ) {
		Map<String, String> objects = new TreeMap<String, String>();
		for( String table : names(db, "table") ) {
			TreeSet<String> columns = new TreeSet<String>();
			Cursor c = db.rawQuery("PRAGMA table_info(\"" + table + "\")", (String[]) null);
			try {
				while( c.moveToNext() ) {
					columns.add(c.getString(1) + " " + c.getString(2).toUpperCase()
							+ (c.getInt(3) != 0 ? " NOT NULL" : "")
							+ (c.isNull(4) ? "" : " DEFAULT " + c.getString(4))
							+ (c.getInt(5) != 0 ? " PRIMARY KEY" : ""));
				}
			}
			finally {
				c.close();
			}
			TreeSet<String> indexes = new TreeSet<String>();
			c = db.rawQuery("PRAGMA index_list(\"" + table + "\")", (String[]) null);
			try {
				while( c.moveToNext() ) {
					String index = c.getString(1);
					// One made for a UNIQUE constraint is named by its position
					String name = index.startsWith("sqlite_autoindex_") ? "(constraint)" : index;
					indexes.add((c.getInt(2) != 0 ? "UNIQUE " : "") + name + " " + indexColumns(db, index));
				}
			}
			finally {
				c.close();
			}
			objects.put(table, "table " + table + "\n  columns " + columns + "\n  indexes " + indexes);
		}
		StringBuilder description = new StringBuilder();
		for( String each : objects.values() ) {
			description.append(each).append('\n');
		}
		for( String type : new String[] { "trigger", "view" } ) {
			for( String name : names(db, type) ) {
				description.append(type).append(' ').append(name).append('\n');
			}
		}
		return description.toString();
	}

	private static List<String> names( SQLiteDatabase db, String type ) {
		List<String> names = new ArrayList<String>();
		Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = ? AND name NOT LIKE 'sqlite_%'"
				+ " AND name <> 'android_metadata' ORDER BY name", new String[] { type });
		try {
			while( c.moveToNext() ) {
				names.add(c.getString(0));
			}
		}
		finally {
			c.close();
		}
		return names;
	}

	private static List<String> indexColumns( SQLiteDatabase db, String index ) {
		List<String> columns = new ArrayList<String>();
		Cursor c = db.rawQuery("PRAGMA index_info(\"" + index + "\")", (String[]) null);
		try {
			while( c.moveToNext() ) {
				columns.add(c.getString(2));
			}
		}
		finally {
			c.close();
		}
		return columns;
	}

	/** @return the names of a table's columns */
	public static TreeSet<String> columns( SQLiteDatabase db, String table ) {
		TreeSet<String> columns = new TreeSet<String>();
		Cursor c = db.rawQuery("PRAGMA table_info(\"" + table + "\")", (String[]) null);
		try {
			while( c.moveToNext() ) {
				columns.add(c.getString(1));
			}
		}
		finally {
			c.close();
		}
		return columns;
	}
}
