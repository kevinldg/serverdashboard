package com.github.kevinldg.backend.maintenance;

import java.time.Instant;

/**
 * @param message   information text for the maintenance page (may be empty)
 * @param updatedAt null if maintenance mode was never changed
 */
public record MaintenanceStatus(boolean enabled, String message, Instant updatedAt, String updatedBy) {

    /** The part that is public (shown on the maintenance page without login). */
    public record PublicStatus(boolean enabled, String message) {
    }

    public PublicStatus toPublic() {
        return new PublicStatus(enabled, message);
    }
}
