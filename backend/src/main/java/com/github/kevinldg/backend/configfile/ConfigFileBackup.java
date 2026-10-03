package com.github.kevinldg.backend.configfile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The content of a configuration file before its last save through the application (one version per file).
 * Keyed by container name and path, so it survives recreating the container.
 */
@Document("config_file_backups")
@Getter
@Setter
@NoArgsConstructor
public class ConfigFileBackup {

    /** {@code <container name>:<path>} */
    @Id
    private String id;

    private String content;

    /** When the content was replaced. */
    private Instant replacedAt;

    /** Who replaced it. */
    private String replacedBy;

    static String idOf(String containerName, String path) {
        return containerName + ":" + path;
    }
}
