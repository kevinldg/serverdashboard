package com.github.kevinldg.backend.announcement;

import java.time.Instant;

/**
 * An announcement currently shown on the dashboard.
 */
public record VisibleAnnouncement(String id, String title, String message, Instant startsAt, Instant endsAt) {
}
