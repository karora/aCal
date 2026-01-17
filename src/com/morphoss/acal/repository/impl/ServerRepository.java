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

package com.morphoss.acal.repository.impl;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.repository.IServerRepository;

/**
 * Repository implementation for CalDAV/CardDAV servers.
 * Wraps Servers provider.
 */
public class ServerRepository implements IServerRepository {

    private final ContentResolver contentResolver;

    public ServerRepository(Context context) {
        this.contentResolver = context.getContentResolver();
    }

    @Override
    public List<ContentValues> getAllServers() {
        List<ContentValues> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(Servers.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ContentValues cv = new ContentValues();
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        String colName = cursor.getColumnName(i);
                        int type = cursor.getType(i);
                        switch (type) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                cv.put(colName, cursor.getLong(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                cv.put(colName, cursor.getDouble(i));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                cv.put(colName, cursor.getString(i));
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                cv.put(colName, cursor.getBlob(i));
                                break;
                        }
                    }
                    result.add(cv);
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    @Override
    public List<ContentValues> getActiveServers() {
        List<ContentValues> all = getAllServers();
        List<ContentValues> active = new ArrayList<>();
        for (ContentValues cv : all) {
            Integer isActive = cv.getAsInteger(Servers.ACTIVE);
            if (isActive != null && isActive == 1) {
                active.add(cv);
            }
        }
        return active;
    }

    @Override
    public ContentValues getServerById(long serverId) {
        ContentValues result = null;
        Cursor cursor = null;
        try {
            Uri uri = Uri.withAppendedPath(Servers.CONTENT_URI, String.valueOf(serverId));
            cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                result = new ContentValues();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    String colName = cursor.getColumnName(i);
                    int type = cursor.getType(i);
                    switch (type) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            result.put(colName, cursor.getLong(i));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            result.put(colName, cursor.getDouble(i));
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            result.put(colName, cursor.getString(i));
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            result.put(colName, cursor.getBlob(i));
                            break;
                    }
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    @Override
    public ContentValues getServerByName(String name) {
        List<ContentValues> all = getAllServers();
        for (ContentValues cv : all) {
            String serverName = cv.getAsString(Servers.FRIENDLY_NAME);
            if (name != null && name.equals(serverName)) {
                return cv;
            }
        }
        return null;
    }

    @Override
    public boolean isServerActive(long serverId) {
        ContentValues cv = getServerById(serverId);
        if (cv != null) {
            Integer isActive = cv.getAsInteger(Servers.ACTIVE);
            return isActive != null && isActive == 1;
        }
        return false;
    }

    @Override
    public String getServerName(long serverId) {
        ContentValues cv = getServerById(serverId);
        if (cv != null) {
            return cv.getAsString(Servers.FRIENDLY_NAME);
        }
        return "";
    }

    @Override
    public long addServer(ContentValues server) {
        Uri uri = contentResolver.insert(Servers.CONTENT_URI, server);
        if (uri != null) {
            try {
                return Long.parseLong(uri.getLastPathSegment());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public boolean updateServer(long serverId, ContentValues server) {
        Uri uri = Uri.withAppendedPath(Servers.CONTENT_URI, String.valueOf(serverId));
        int rows = contentResolver.update(uri, server, null, null);
        return rows > 0;
    }

    @Override
    public boolean deleteServer(long serverId) {
        Uri uri = Uri.withAppendedPath(Servers.CONTENT_URI, String.valueOf(serverId));
        int rows = contentResolver.delete(uri, null, null);
        return rows > 0;
    }
}
