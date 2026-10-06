package com.github.kevinldg.backend.audit;

import java.util.List;

/**
 * One page of audit log entries, newest first.
 *
 * @param page zero-based page number
 */
public record AuditLogPage(List<AuditEntryResponse> entries, int page, int size, long totalElements, int totalPages) {
}
