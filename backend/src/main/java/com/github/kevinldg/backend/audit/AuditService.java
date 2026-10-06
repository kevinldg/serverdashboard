package com.github.kevinldg.backend.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records relevant activities in the audit log (collection {@code audit_log}) and writes them to the application log.
 * <p>
 * Recording is best effort: if the entry cannot be saved, the action itself still succeeds and a warning is logged.
 */
@Slf4j
@Service
public class AuditService {

    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_PAGE_SIZE = 200;
    /** The deduplication cache is cleaned up once it holds this many entries. */
    private static final int DEDUPLICATION_CLEANUP_SIZE = 1000;

    private final AuditLogRepository repository;
    private final MongoOperations mongoOperations;
    private final AuditProperties properties;
    private final Clock clock;
    /** Last time an event was recorded by {@link #recordDeduplicated}, per event key (single backend instance). */
    private final Map<String, Instant> lastRecorded = new ConcurrentHashMap<>();

    public AuditService(AuditLogRepository repository, MongoOperations mongoOperations, AuditProperties properties,
                        Clock clock) {
        this.repository = repository;
        this.mongoOperations = mongoOperations;
        this.properties = properties;
        this.clock = clock;
    }

    public void record(AuditEvent event) {
        AuditEntry entry = toEntry(event);
        if (entry.getOutcome() == AuditOutcome.SUCCESS) {
            log.info("Audit {}: user '{}': {}{}", entry.getAction(), forLog(entry.getActor()), forLog(entry.getSummary()),
                    formatDetails(entry));
        } else {
            log.warn("Audit {} ({}): user '{}': {}{}", entry.getAction(), entry.getOutcome(), forLog(entry.getActor()),
                    forLog(entry.getSummary()), formatDetails(entry));
        }

        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("The audit log entry could not be saved: {}", e.toString());
        }
    }

    /**
     * Records the event unless the same user caused the same event (action, outcome, target, summary) within
     * {@link AuditProperties#deduplication()}. Used for frequent events such as viewing environment variables,
     * which happens on every visit of a container's details page.
     */
    public void recordDeduplicated(AuditEvent event) {
        Instant now = clock.instant();
        Instant threshold = now.minus(properties.deduplication());
        String key = String.join("\n", String.valueOf(event.actor()), event.action().name(), event.outcome().name(),
                String.valueOf(event.target()), String.valueOf(event.summary()));

        Instant previous = lastRecorded.get(key);
        if (previous != null && previous.isAfter(threshold)) {
            return;
        }
        lastRecorded.put(key, now);
        if (lastRecorded.size() > DEDUPLICATION_CLEANUP_SIZE) {
            lastRecorded.values().removeIf(time -> !time.isAfter(threshold));
        }
        record(event);
    }

    /** Entries matching the filter, newest first. */
    public AuditLogPage search(AuditLogFilter filter, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);

        Query query = new Query(filter.toCriteria());
        long total = mongoOperations.count(query, AuditEntry.class);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) pageNumber * pageSize)
                .limit(pageSize);
        List<AuditEntryResponse> entries = mongoOperations.find(query, AuditEntry.class).stream()
                .map(AuditEntryResponse::of)
                .toList();
        return new AuditLogPage(entries, pageNumber, pageSize, total, (int) ((total + pageSize - 1) / pageSize));
    }

    /** All usernames that appear in the audit log, for the user filter. */
    public List<String> listActors() {
        return mongoOperations.findDistinct(new Query(), "actor", AuditEntry.class, String.class).stream()
                .filter(StringUtils::hasText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Filter for {@link #search}; {@code null} values do not restrict the result. */
    public record AuditLogFilter(AuditCategory category, String actor, AuditOutcome outcome, Instant from, Instant to) {

        Criteria toCriteria() {
            List<Criteria> criteria = new ArrayList<>();
            if (category != null) {
                criteria.add(Criteria.where("category").is(category));
            }
            if (StringUtils.hasText(actor)) {
                criteria.add(Criteria.where("actor").is(actor));
            }
            if (outcome != null) {
                criteria.add(Criteria.where("outcome").is(outcome));
            }
            if (from != null || to != null) {
                Criteria timestamp = Criteria.where("timestamp");
                if (from != null) {
                    timestamp = timestamp.gte(from);
                }
                if (to != null) {
                    timestamp = timestamp.lt(to);
                }
                criteria.add(timestamp);
            }
            return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
        }
    }

    private AuditEntry toEntry(AuditEvent event) {
        AuditEntry entry = new AuditEntry();
        entry.setTimestamp(clock.instant());
        entry.setActor(truncate(event.actor()));
        entry.setCategory(event.action().getCategory());
        entry.setAction(event.action());
        entry.setOutcome(event.outcome());
        entry.setTarget(truncate(event.target()));
        entry.setSummary(truncate(event.summary()));
        Map<String, String> details = new LinkedHashMap<>();
        event.details().forEach((key, value) -> details.put(key, truncate(value)));
        entry.setDetails(details);
        entry.setIp(event.ip());
        return entry;
    }

    private static String formatDetails(AuditEntry entry) {
        return entry.getDetails().isEmpty() ? "" : " " + forLog(entry.getDetails().toString());
    }

    /** Line breaks and other control characters (e.g. in an attempted username) must not forge log lines. */
    private static String forLog(String text) {
        return text == null ? null : text.replaceAll("\\p{Cntrl}", " ");
    }

    /** Limits free text (e.g. error messages, attempted usernames), so entries stay small. */
    static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }
}
