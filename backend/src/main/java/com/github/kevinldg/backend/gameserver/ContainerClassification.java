package com.github.kevinldg.backend.gameserver;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A manual classification (game server, category, or neither), which overrides automatic detection.
 * <p>
 * Keyed by container name, so the classification survives recreating the container (e.g. after an image update).
 */
@Document("container_classifications")
@Getter
@Setter
@NoArgsConstructor
public class ContainerClassification {

    /** The container name (without leading slash). */
    @Id
    private String containerName;

    private boolean gameServer;

    /** Null for a generic game server (or if not a game server). */
    private String profileId;

    /** A {@link com.github.kevinldg.backend.category.ContainerCategory}; only if not a game server. */
    private String categoryId;

    private Instant updatedAt;

    private String updatedBy;
}
