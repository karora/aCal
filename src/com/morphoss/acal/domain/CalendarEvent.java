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
 * Immutable domain entity representing a calendar event.
 * This is a clean domain object decoupled from database and UI concerns.
 */
public final class CalendarEvent {

    private final long resourceId;
    private final long collectionId;
    private final String summary;
    private final String description;
    private final String location;
    private final AcalDateTime startDate;
    private final AcalDateTime endDate;
    private final boolean allDay;
    private final String recurrenceId;
    private final int colour;
    private final boolean hasAlarm;

    private CalendarEvent(Builder builder) {
        this.resourceId = builder.resourceId;
        this.collectionId = builder.collectionId;
        this.summary = builder.summary;
        this.description = builder.description;
        this.location = builder.location;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.allDay = builder.allDay;
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

    public String getLocation() {
        return location;
    }

    public AcalDateTime getStartDate() {
        return startDate;
    }

    public AcalDateTime getEndDate() {
        return endDate;
    }

    public boolean isAllDay() {
        return allDay;
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

    /**
     * Creates a new builder for CalendarEvent.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-filled with this event's values for modification.
     */
    public Builder toBuilder() {
        return new Builder()
                .resourceId(resourceId)
                .collectionId(collectionId)
                .summary(summary)
                .description(description)
                .location(location)
                .startDate(startDate)
                .endDate(endDate)
                .allDay(allDay)
                .recurrenceId(recurrenceId)
                .colour(colour)
                .hasAlarm(hasAlarm);
    }

    /**
     * Builder for CalendarEvent.
     */
    public static final class Builder {
        private long resourceId;
        private long collectionId;
        private String summary;
        private String description;
        private String location;
        private AcalDateTime startDate;
        private AcalDateTime endDate;
        private boolean allDay;
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

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder startDate(AcalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(AcalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder allDay(boolean allDay) {
            this.allDay = allDay;
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

        public CalendarEvent build() {
            return new CalendarEvent(this);
        }
    }

    @Override
    public String toString() {
        return "CalendarEvent{" +
                "resourceId=" + resourceId +
                ", summary='" + summary + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
