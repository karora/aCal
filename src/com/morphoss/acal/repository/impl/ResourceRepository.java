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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.cachemanager.CacheResponse;
import com.morphoss.acal.database.cachemanager.CacheResponseListener;
import com.morphoss.acal.database.cachemanager.requests.CRObjectsInRange;
import com.morphoss.acal.database.cachemanager.requests.CRTodosByType;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.domain.CalendarEvent;
import com.morphoss.acal.domain.CalendarNote;
import com.morphoss.acal.domain.CalendarTask;
import com.morphoss.acal.repository.IResourceRepository;
import com.morphoss.acal.repository.mapper.CacheObjectMapper;

/**
 * Repository implementation for calendar resources.
 * Wraps CacheManager and ResourceManager to provide a clean API.
 */
public class ResourceRepository implements IResourceRepository {

    private static final long TIMEOUT_MS = 10000;

    private final Context context;
    private CacheManager cacheManager;
    private ResourceManager resourceManager;

    public ResourceRepository(Context context) {
        this.context = context;
    }

    private CacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = CacheManager.getInstance(context);
        }
        return cacheManager;
    }

    private ResourceManager getResourceManager() {
        if (resourceManager == null) {
            resourceManager = ResourceManager.getInstance(context);
        }
        return resourceManager;
    }

    @Override
    public List<CalendarEvent> getEventsInRange(AcalDateRange range) {
        final List<CalendarEvent> events = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ArrayList<CacheObject>> resultRef = new AtomicReference<>();

        try {
            CRObjectsInRange request = CRObjectsInRange.EventsInRange(range,
                    new CacheResponseListener<ArrayList<CacheObject>>() {
                        @Override
                        public void cacheResponse(CacheResponse<ArrayList<CacheObject>> response) {
                            if (response != null) {
                                resultRef.set(response.result());
                            }
                            latch.countDown();
                        }
                    });

            getCacheManager().sendRequest(request);
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            ArrayList<CacheObject> result = resultRef.get();
            if (result != null) {
                for (CacheObject co : result) {
                    if (CacheObjectMapper.isEvent(co)) {
                        CalendarEvent event = CacheObjectMapper.toCalendarEvent(co);
                        if (event != null) {
                            events.add(event);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log and return empty list
        }

        return events;
    }

    @Override
    public CalendarEvent getEventById(long resourceId) {
        // Would need to implement resource lookup
        return null;
    }

    @Override
    public List<CalendarTask> getTasks(boolean includeCompleted) {
        final List<CalendarTask> tasks = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ArrayList<CacheObject>> resultRef = new AtomicReference<>();

        try {
            CRTodosByType request = new CRTodosByType(includeCompleted, true,
                    new CacheResponseListener<ArrayList<CacheObject>>() {
                        @Override
                        public void cacheResponse(CacheResponse<ArrayList<CacheObject>> response) {
                            if (response != null) {
                                resultRef.set(response.result());
                            }
                            latch.countDown();
                        }
                    });

            getCacheManager().sendRequest(request);
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            ArrayList<CacheObject> result = resultRef.get();
            if (result != null) {
                for (CacheObject co : result) {
                    CalendarTask task = CacheObjectMapper.toCalendarTask(co);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }
        } catch (Exception e) {
            // Log and return empty list
        }

        return tasks;
    }

    @Override
    public List<CalendarTask> getTasksInRange(AcalDateRange range) {
        // Use getTasks for now as task range queries aren't directly supported
        return getTasks(false);
    }

    @Override
    public CalendarTask getTaskById(long resourceId) {
        return null;
    }

    @Override
    public List<CalendarNote> getNotes() {
        return new ArrayList<>();
    }

    @Override
    public List<CalendarNote> getNotesInRange(AcalDateRange range) {
        final List<CalendarNote> notes = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ArrayList<CacheObject>> resultRef = new AtomicReference<>();

        try {
            CRObjectsInRange request = new CRObjectsInRange(range,
                    new CacheResponseListener<ArrayList<CacheObject>>() {
                        @Override
                        public void cacheResponse(CacheResponse<ArrayList<CacheObject>> response) {
                            if (response != null) {
                                resultRef.set(response.result());
                            }
                            latch.countDown();
                        }
                    });

            getCacheManager().sendRequest(request);
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            ArrayList<CacheObject> result = resultRef.get();
            if (result != null) {
                for (CacheObject co : result) {
                    if (CacheObjectMapper.isNote(co)) {
                        CalendarNote note = CacheObjectMapper.toCalendarNote(co);
                        if (note != null) {
                            notes.add(note);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log and return empty list
        }

        return notes;
    }

    @Override
    public CalendarNote getNoteById(long resourceId) {
        return null;
    }

    @Override
    public CalendarEvent saveEvent(CalendarEvent event) {
        throw new UnsupportedOperationException("saveEvent not yet implemented");
    }

    @Override
    public CalendarTask saveTask(CalendarTask task) {
        throw new UnsupportedOperationException("saveTask not yet implemented");
    }

    @Override
    public CalendarNote saveNote(CalendarNote note) {
        throw new UnsupportedOperationException("saveNote not yet implemented");
    }

    @Override
    public boolean deleteResource(long resourceId) {
        throw new UnsupportedOperationException("deleteResource not yet implemented");
    }
}
