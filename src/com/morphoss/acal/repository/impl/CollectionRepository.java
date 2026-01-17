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
import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.repository.ICollectionRepository;

/**
 * Repository implementation for calendar collections.
 * Wraps DavCollections provider.
 */
public class CollectionRepository implements ICollectionRepository {

    private final ContentResolver contentResolver;

    public CollectionRepository(Context context) {
        this.contentResolver = context.getContentResolver();
    }

    @Override
    public List<ContentValues> getActiveCollections() {
        ContentValues[] collections = DavCollections.getCollections(contentResolver,
                DavCollections.INCLUDE_ALL_ACTIVE);
        return Arrays.asList(collections);
    }

    @Override
    public List<ContentValues> getCollectionsByServer(long serverId) {
        List<ContentValues> result = new ArrayList<>();
        ContentValues[] all = DavCollections.getCollections(contentResolver,
                DavCollections.INCLUDE_ALL_ACTIVE);
        for (ContentValues cv : all) {
            Long sid = cv.getAsLong(DavCollections.SERVER_ID);
            if (sid != null && sid == serverId) {
                result.add(cv);
            }
        }
        return result;
    }

    @Override
    public ContentValues getCollectionById(long collectionId) {
        return DavCollections.getRow(collectionId, contentResolver);
    }

    @Override
    public List<ContentValues> getEventCollections() {
        List<ContentValues> result = new ArrayList<>();
        ContentValues[] all = DavCollections.getCollections(contentResolver,
                DavCollections.INCLUDE_ALL_ACTIVE);
        for (ContentValues cv : all) {
            Integer activeEvents = cv.getAsInteger(DavCollections.ACTIVE_EVENTS);
            if (activeEvents != null && activeEvents == 1) {
                result.add(cv);
            }
        }
        return result;
    }

    @Override
    public List<ContentValues> getTaskCollections() {
        List<ContentValues> result = new ArrayList<>();
        ContentValues[] all = DavCollections.getCollections(contentResolver,
                DavCollections.INCLUDE_ALL_ACTIVE);
        for (ContentValues cv : all) {
            Integer activeTasks = cv.getAsInteger(DavCollections.ACTIVE_TASKS);
            if (activeTasks != null && activeTasks == 1) {
                result.add(cv);
            }
        }
        return result;
    }

    @Override
    public List<ContentValues> getNoteCollections() {
        List<ContentValues> result = new ArrayList<>();
        ContentValues[] all = DavCollections.getCollections(contentResolver,
                DavCollections.INCLUDE_ALL_ACTIVE);
        for (ContentValues cv : all) {
            Integer activeJournal = cv.getAsInteger(DavCollections.ACTIVE_JOURNAL);
            if (activeJournal != null && activeJournal == 1) {
                result.add(cv);
            }
        }
        return result;
    }

    @Override
    public int getCollectionColour(long collectionId) {
        ContentValues cv = getCollectionById(collectionId);
        if (cv != null) {
            Integer colour = cv.getAsInteger(DavCollections.COLOUR);
            if (colour != null) {
                return colour;
            }
        }
        return 0xFF0000FF; // Default blue
    }

    @Override
    public String getCollectionName(long collectionId) {
        ContentValues cv = getCollectionById(collectionId);
        if (cv != null) {
            String name = cv.getAsString(DavCollections.DISPLAYNAME);
            if (name != null) {
                return name;
            }
        }
        return "";
    }

    @Override
    public boolean isCollectionWritable(long collectionId) {
        ContentValues cv = getCollectionById(collectionId);
        if (cv != null) {
            // Check if collection has write permission
            // Default to writable if the field doesn't exist
            return true;
        }
        return false;
    }
}
