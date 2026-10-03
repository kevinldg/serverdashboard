package com.github.kevinldg.backend.maintenance;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The maintenance mode setting. There is exactly one document with {@link #ID}.
 */
@Document("settings")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceSettings {

    public static final String ID = "maintenance";

    @Id
    private String id = ID;

    private boolean enabled;

    /** Shown on the maintenance page; plain text. */
    private String message;

    private Instant updatedAt;

    private String updatedBy;
}
