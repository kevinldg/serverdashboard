package com.github.kevinldg.backend.audit;

import com.github.kevinldg.backend.audit.AuditService.AuditLogFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-only access to the audit log. Entries cannot be changed or deleted through the API.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
@PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    /**
     * @param from inclusive (ISO-8601 timestamp)
     * @param to   exclusive (ISO-8601 timestamp)
     */
    @GetMapping
    public AuditLogPage search(@RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(AuditService.MAX_PAGE_SIZE) int size,
                               @RequestParam(required = false) AuditCategory category,
                               @RequestParam(required = false) String actor,
                               @RequestParam(required = false) AuditOutcome outcome,
                               @RequestParam(required = false) Instant from,
                               @RequestParam(required = false) Instant to) {
        return auditService.search(new AuditLogFilter(category, actor, outcome, from, to), page, size);
    }

    @GetMapping("/actors")
    public List<String> listActors() {
        return auditService.listActors();
    }
}
