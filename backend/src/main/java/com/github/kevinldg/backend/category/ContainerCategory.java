package com.github.kevinldg.backend.category;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A category that can be assigned to containers manually instead of a game server classification,
 * e.g. "System" for a reverse proxy or "Communication" for a voice server.
 */
@Document("container_categories")
@Getter
@Setter
@NoArgsConstructor
public class ContainerCategory {

    @Id
    private String id;

    /** Unique, ignoring case. */
    private String name;

    private CategoryColor color;

    private Instant createdAt;

    private Instant updatedAt;
}
