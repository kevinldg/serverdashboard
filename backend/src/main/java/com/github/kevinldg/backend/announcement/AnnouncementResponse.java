package com.github.kevinldg.backend.announcement;

import java.time.Instant;

/**
 * An announcement as shown in the announcement management.
 *
 * @param status whether the announcement is currently shown on the dashboard
 */
public record AnnouncementResponse(
        String id,
        String title,
        String message,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Status status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {

    public enum Status {
        /** Active and within its time window. */
        VISIBLE,
        /** Active, but the start time has not been reached yet. */
        SCHEDULED,
        /** Active, but the end time has passed. */
        EXPIRED,
        /** Deactivated. */
        INACTIVE
    }
}
