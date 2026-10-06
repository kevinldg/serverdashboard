package com.github.kevinldg.backend.audit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One audit log entry. Entries are never changed; MongoDB removes them after the retention period
 * (TTL index on {@code timestamp}, see {@link AuditProperties#retention()}).
 */
@Document("audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    private String id;

    private Instant timestamp;

    /** Username at the time of the action; for failed logins the attempted username. */
    private String actor;

    private AuditCategory category;

    private AuditAction action;

    private AuditOutcome outcome;

    /** What the action was about, e.g. a container name, username or role name. */
    private String target;

    /** Readable description, e.g. "Stopped container 'minecraft'". */
    private String summary;

    /** Additional information (never secrets), e.g. changed fields or the error message. */
    private Map<String, String> details = new LinkedHashMap<>();

    /** Client address; only stored for login and logout events. */
    private String ip;
}
