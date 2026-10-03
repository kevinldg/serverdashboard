package com.github.kevinldg.backend.announcement;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An announcement shown on the dashboard while it is active and within its optional time window.
 */
@Document("announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    private String id;

    private String title;

    /** Plain text; line breaks are preserved, no HTML or Markdown. */
    private String message;

    private boolean active;

    /** Optional: not shown before this time. */
    private Instant startsAt;

    /** Optional: no longer shown from this time on. */
    private Instant endsAt;

    private Instant createdAt;

    private String createdBy;

    private Instant updatedAt;

    private String updatedBy;
}
