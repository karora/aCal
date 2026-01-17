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

package com.morphoss.acal.domain;

import com.morphoss.acal.acaltime.AcalDateTime;

/**
 * Immutable domain entity representing a calendar task (VTODO).
 * This is a clean domain object decoupled from database and UI concerns.
 */
public final class CalendarTask {

    /**
     * Task completion status.
     */
    public enum Status {
        NEEDS_ACTION,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    /**
     * Task priority (RFC 5545 values).
     */
    public enum Priority {
        UNDEFINED(0),
        HIGH(1),
        MEDIUM(5),
        LOW(9);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Priority fromValue(int value) {
            if (value == 0) return UNDEFINED;
            if (value >= 1 && value <= 4) return HIGH;
            if (value == 5) return MEDIUM;
            if (value >= 6 && value <= 9) return LOW;
            return UNDEFINED;
        }
    }

    private final long resourceId;
    private final long collectionId;
    private final String summary;
    private final String description;
    private final AcalDateTime dueDate;
    private final AcalDateTime startDate;
    private final AcalDateTime completedDate;
    private final Status status;
    private final Priority priority;
    private final int percentComplete;
    private final String recurrenceId;
    private final int colour;
    private final boolean hasAlarm;

    private CalendarTask(Builder builder) {
        this.resourceId = builder.resourceId;
        this.collectionId = builder.collectionId;
        this.summary = builder.summary;
        this.description = builder.description;
        this.dueDate = builder.dueDate;
        this.startDate = builder.startDate;
        this.completedDate = builder.completedDate;
        this.status = builder.status;
        this.priority = builder.priority;
        this.percentComplete = builder.percentComplete;
        this.recurrenceId = builder.recurrenceId;
        this.colour = builder.colour;
        this.hasAlarm = builder.hasAlarm;
    }

    public long getResourceId() {
        return resourceId;
    }

    public long getCollectionId() {
        return collectionId;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public AcalDateTime getDueDate() {
        return dueDate;
    }

    public AcalDateTime getStartDate() {
        return startDate;
    }

    public AcalDateTime getCompletedDate() {
        return completedDate;
    }

    public Status getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public int getPercentComplete() {
        return percentComplete;
    }

    public String getRecurrenceId() {
        return recurrenceId;
    }

    public int getColour() {
        return colour;
    }

    public boolean hasAlarm() {
        return hasAlarm;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isOverdue() {
        if (dueDate == null || isCompleted()) return false;
        return dueDate.before(new AcalDateTime());
    }

    /**
     * Creates a new builder for CalendarTask.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-filled with this task's values for modification.
     */
    public Builder toBuilder() {
        return new Builder()
                .resourceId(resourceId)
                .collectionId(collectionId)
                .summary(summary)
                .description(description)
                .dueDate(dueDate)
                .startDate(startDate)
                .completedDate(completedDate)
                .status(status)
                .priority(priority)
                .percentComplete(percentComplete)
                .recurrenceId(recurrenceId)
                .colour(colour)
                .hasAlarm(hasAlarm);
    }

    /**
     * Builder for CalendarTask.
     */
    public static final class Builder {
        private long resourceId;
        private long collectionId;
        private String summary;
        private String description;
        private AcalDateTime dueDate;
        private AcalDateTime startDate;
        private AcalDateTime completedDate;
        private Status status = Status.NEEDS_ACTION;
        private Priority priority = Priority.UNDEFINED;
        private int percentComplete;
        private String recurrenceId;
        private int colour;
        private boolean hasAlarm;

        private Builder() {
        }

        public Builder resourceId(long resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder collectionId(long collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dueDate(AcalDateTime dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder startDate(AcalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder completedDate(AcalDateTime completedDate) {
            this.completedDate = completedDate;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder percentComplete(int percentComplete) {
            this.percentComplete = percentComplete;
            return this;
        }

        public Builder recurrenceId(String recurrenceId) {
            this.recurrenceId = recurrenceId;
            return this;
        }

        public Builder colour(int colour) {
            this.colour = colour;
            return this;
        }

        public Builder hasAlarm(boolean hasAlarm) {
            this.hasAlarm = hasAlarm;
            return this;
        }

        public CalendarTask build() {
            return new CalendarTask(this);
        }
    }

    @Override
    public String toString() {
        return "CalendarTask{" +
                "resourceId=" + resourceId +
                ", summary='" + summary + '\'' +
                ", dueDate=" + dueDate +
                ", status=" + status +
                '}';
    }
}
