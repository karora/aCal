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

package com.morphoss.acal.repository;

import java.util.List;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.domain.CalendarEvent;
import com.morphoss.acal.domain.CalendarNote;
import com.morphoss.acal.domain.CalendarTask;

/**
 * Repository interface for accessing calendar resources.
 * Provides a clean abstraction over the underlying data storage,
 * returning domain entities instead of raw database objects.
 */
public interface IResourceRepository {

    /**
     * Get all events in a date range.
     *
     * @param range The date range to query
     * @return List of calendar events in the range
     */
    List<CalendarEvent> getEventsInRange(AcalDateRange range);

    /**
     * Get a specific event by resource ID.
     *
     * @param resourceId The resource ID
     * @return The calendar event, or null if not found
     */
    CalendarEvent getEventById(long resourceId);

    /**
     * Get all tasks, optionally filtered by completion status.
     *
     * @param includeCompleted Whether to include completed tasks
     * @return List of calendar tasks
     */
    List<CalendarTask> getTasks(boolean includeCompleted);

    /**
     * Get tasks in a date range.
     *
     * @param range The date range to query
     * @return List of calendar tasks in the range
     */
    List<CalendarTask> getTasksInRange(AcalDateRange range);

    /**
     * Get a specific task by resource ID.
     *
     * @param resourceId The resource ID
     * @return The calendar task, or null if not found
     */
    CalendarTask getTaskById(long resourceId);

    /**
     * Get all notes/journals.
     *
     * @return List of calendar notes
     */
    List<CalendarNote> getNotes();

    /**
     * Get notes in a date range.
     *
     * @param range The date range to query
     * @return List of calendar notes in the range
     */
    List<CalendarNote> getNotesInRange(AcalDateRange range);

    /**
     * Get a specific note by resource ID.
     *
     * @param resourceId The resource ID
     * @return The calendar note, or null if not found
     */
    CalendarNote getNoteById(long resourceId);

    /**
     * Save a calendar event (insert or update).
     *
     * @param event The event to save
     * @return The saved event with updated resource ID
     */
    CalendarEvent saveEvent(CalendarEvent event);

    /**
     * Save a calendar task (insert or update).
     *
     * @param task The task to save
     * @return The saved task with updated resource ID
     */
    CalendarTask saveTask(CalendarTask task);

    /**
     * Save a calendar note (insert or update).
     *
     * @param note The note to save
     * @return The saved note with updated resource ID
     */
    CalendarNote saveNote(CalendarNote note);

    /**
     * Delete a resource by ID.
     *
     * @param resourceId The resource ID to delete
     * @return true if deleted, false if not found
     */
    boolean deleteResource(long resourceId);
}
