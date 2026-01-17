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

package com.morphoss.acal.repository.mapper;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.domain.CalendarEvent;
import com.morphoss.acal.domain.CalendarNote;
import com.morphoss.acal.domain.CalendarTask;

/**
 * Mapper for converting between CacheObject and domain entities.
 */
public class CacheObjectMapper {

    /**
     * Convert a CacheObject to a CalendarEvent domain entity.
     *
     * @param cacheObject The cache object to convert
     * @return The domain entity
     */
    public static CalendarEvent toCalendarEvent(CacheObject cacheObject) {
        if (cacheObject == null) return null;

        return CalendarEvent.builder()
                .resourceId(cacheObject.getResourceId())
                .collectionId(cacheObject.getCollectionId())
                .summary(cacheObject.getSummary())
                .location(cacheObject.getLocation())
                .startDate(cacheObject.getStartDateTime())
                .endDate(cacheObject.getEndDateTime())
                .allDay(cacheObject.isAllDay())
                .recurrenceId(cacheObject.getRecurrenceId())
                .hasAlarm(cacheObject.hasAlarms())
                .build();
    }

    /**
     * Convert a CacheObject to a CalendarTask domain entity.
     *
     * @param cacheObject The cache object to convert
     * @return The domain entity
     */
    public static CalendarTask toCalendarTask(CacheObject cacheObject) {
        if (cacheObject == null) return null;

        CalendarTask.Status status = CalendarTask.Status.NEEDS_ACTION;
        if (cacheObject.isCompleted()) {
            status = CalendarTask.Status.COMPLETED;
        }

        return CalendarTask.builder()
                .resourceId(cacheObject.getResourceId())
                .collectionId(cacheObject.getCollectionId())
                .summary(cacheObject.getSummary())
                .dueDate(cacheObject.getEndDateTime())
                .startDate(cacheObject.getStartDateTime())
                .completedDate(cacheObject.getCompletedDateTime())
                .status(status)
                .recurrenceId(cacheObject.getRecurrenceId())
                .hasAlarm(cacheObject.hasAlarms())
                .build();
    }

    /**
     * Convert a CacheObject to a CalendarNote domain entity.
     *
     * @param cacheObject The cache object to convert
     * @return The domain entity
     */
    public static CalendarNote toCalendarNote(CacheObject cacheObject) {
        if (cacheObject == null) return null;

        return CalendarNote.builder()
                .resourceId(cacheObject.getResourceId())
                .collectionId(cacheObject.getCollectionId())
                .summary(cacheObject.getSummary())
                .dateStamp(cacheObject.getStartDateTime())
                .recurrenceId(cacheObject.getRecurrenceId())
                .build();
    }

    /**
     * Check if a CacheObject represents an event.
     */
    public static boolean isEvent(CacheObject cacheObject) {
        return cacheObject != null && cacheObject.isEvent();
    }

    /**
     * Check if a CacheObject represents a task.
     */
    public static boolean isTask(CacheObject cacheObject) {
        return cacheObject != null && cacheObject.isTodo();
    }

    /**
     * Check if a CacheObject represents a note/journal.
     * Note: CacheObject doesn't have isJournal(), so we check it's not event or todo.
     */
    public static boolean isNote(CacheObject cacheObject) {
        return cacheObject != null && !cacheObject.isEvent() && !cacheObject.isTodo();
    }
}
