package com.github.kevinldg.backend.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param startsAt optional start of the display period
 * @param endsAt   optional end of the display period; must be after {@code startsAt}
 */
public record AnnouncementRequest(
        @NotBlank
        @Size(max = AnnouncementService.MAX_TITLE_LENGTH)
        String title,

        @NotBlank
        @Size(max = AnnouncementService.MAX_MESSAGE_LENGTH)
        String message,

        @NotNull
        Boolean active,

        Instant startsAt,

        Instant endsAt
) {
}
