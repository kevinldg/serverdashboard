package com.github.kevinldg.backend.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Audit log settings.
 *
 * @param retention     entries are deleted automatically after this time
 * @param deduplication repeated events of the same kind (e.g. a user viewing the same configuration file again) are
 *                      recorded at most once within this time, see {@link AuditService#recordDeduplicated}
 */
@ConfigurationProperties("app.audit")
public record AuditProperties(Duration retention, Duration deduplication) {
}
