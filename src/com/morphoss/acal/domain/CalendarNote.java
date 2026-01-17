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
 * Immutable domain entity representing a calendar note/journal (VJOURNAL).
 * This is a clean domain object decoupled from database and UI concerns.
 */
public final class CalendarNote {

    private final long resourceId;
    private final long collectionId;
    private final String summary;
    private final String description;
    private final AcalDateTime dateStamp;
    private final String recurrenceId;
    private final int colour;

    private CalendarNote(Builder builder) {
        this.resourceId = builder.resourceId;
        this.collectionId = builder.collectionId;
        this.summary = builder.summary;
        this.description = builder.description;
        this.dateStamp = builder.dateStamp;
        this.recurrenceId = builder.recurrenceId;
        this.colour = builder.colour;
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

    public AcalDateTime getDateStamp() {
        return dateStamp;
    }

    public String getRecurrenceId() {
        return recurrenceId;
    }

    public int getColour() {
        return colour;
    }

    /**
     * Creates a new builder for CalendarNote.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-filled with this note's values for modification.
     */
    public Builder toBuilder() {
        return new Builder()
                .resourceId(resourceId)
                .collectionId(collectionId)
                .summary(summary)
                .description(description)
                .dateStamp(dateStamp)
                .recurrenceId(recurrenceId)
                .colour(colour);
    }

    /**
     * Builder for CalendarNote.
     */
    public static final class Builder {
        private long resourceId;
        private long collectionId;
        private String summary;
        private String description;
        private AcalDateTime dateStamp;
        private String recurrenceId;
        private int colour;

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

        public Builder dateStamp(AcalDateTime dateStamp) {
            this.dateStamp = dateStamp;
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

        public CalendarNote build() {
            return new CalendarNote(this);
        }
    }

    @Override
    public String toString() {
        return "CalendarNote{" +
                "resourceId=" + resourceId +
                ", summary='" + summary + '\'' +
                ", dateStamp=" + dateStamp +
                '}';
    }
}
