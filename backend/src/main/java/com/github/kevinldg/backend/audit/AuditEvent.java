package com.github.kevinldg.backend.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An event to record with {@link AuditService}. Created with {@link #success}, {@link #failure} or {@link #denied};
 * details and the client address are added with {@link #detail} and {@link #ip}.
 * <p>
 * Never put secrets (passwords, environment variable values, file contents) into an event.
 */
public record AuditEvent(String actor, AuditAction action, AuditOutcome outcome, String target, String summary,
                         Map<String, String> details, String ip) {

    public AuditEvent {
        // Keeps the order in which details were added
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static AuditEvent success(String actor, AuditAction action, String target, String summary) {
        return new AuditEvent(actor, action, AuditOutcome.SUCCESS, target, summary, Map.of(), null);
    }

    /** A failed action; {@code error} is the (user-facing) error message. */
    public static AuditEvent failure(String actor, AuditAction action, String target, String summary, String error) {
        return new AuditEvent(actor, action, AuditOutcome.FAILURE, target, summary, Map.of(), null)
                .detail("error", error);
    }

    public static AuditEvent denied(String actor, AuditAction action, String target, String summary) {
        return new AuditEvent(actor, action, AuditOutcome.DENIED, target, summary, Map.of(), null);
    }

    /** Adds a detail; {@code null} values are left out. */
    public AuditEvent detail(String key, Object value) {
        if (value == null) {
            return this;
        }
        Map<String, String> newDetails = new LinkedHashMap<>(details);
        newDetails.put(key, String.valueOf(value));
        return new AuditEvent(actor, action, outcome, target, summary, newDetails, ip);
    }

    public AuditEvent ip(String clientAddress) {
        return new AuditEvent(actor, action, outcome, target, summary, details, clientAddress);
    }
}
