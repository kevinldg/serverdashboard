package com.github.kevinldg.backend.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEntryResponse(String id, Instant timestamp, String actor, AuditCategory category, AuditAction action,
                                 AuditOutcome outcome, String target, String summary, Map<String, String> details,
                                 String ip) {

    static AuditEntryResponse of(AuditEntry entry) {
        return new AuditEntryResponse(entry.getId(), entry.getTimestamp(), entry.getActor(), entry.getCategory(),
                entry.getAction(), entry.getOutcome(), entry.getTarget(), entry.getSummary(),
                entry.getDetails() == null ? Map.of() : entry.getDetails(), entry.getIp());
    }
}
