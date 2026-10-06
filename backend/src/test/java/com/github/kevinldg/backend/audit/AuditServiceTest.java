package com.github.kevinldg.backend.audit;

import com.github.kevinldg.backend.audit.AuditService.AuditLogFilter;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final MongoOperations mongoOperations = mock(MongoOperations.class);
    private final Clock clock = mock(Clock.class);
    private AuditService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        service = new AuditService(repository, mongoOperations,
                new AuditProperties(Duration.ofDays(365), Duration.ofMinutes(15)), clock);
    }

    @Test
    void recordsEventWithCategoryOfItsAction() {
        service.record(AuditEvent.success("kevin", AuditAction.CONTAINER_STOP, "minecraft", "Stopped container 'minecraft'")
                .detail("volumes", "kept")
                .detail("ignored", null));

        AuditEntry entry = savedEntry();
        assertThat(entry.getTimestamp()).isEqualTo(NOW);
        assertThat(entry.getActor()).isEqualTo("kevin");
        assertThat(entry.getCategory()).isEqualTo(AuditCategory.CONTAINER);
        assertThat(entry.getAction()).isEqualTo(AuditAction.CONTAINER_STOP);
        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.getTarget()).isEqualTo("minecraft");
        assertThat(entry.getSummary()).isEqualTo("Stopped container 'minecraft'");
        assertThat(entry.getDetails()).containsExactly(org.assertj.core.api.Assertions.entry("volumes", "kept"));
        assertThat(entry.getIp()).isNull();
    }

    @Test
    void failureKeepsErrorAndLoginKeepsAddress() {
        service.record(AuditEvent.failure("alice", AuditAction.LOGIN_FAILED, "alice", "Login failed: wrong password", null)
                .ip("192.168.178.20"));

        AuditEntry entry = savedEntry();
        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(entry.getCategory()).isEqualTo(AuditCategory.AUTHENTICATION);
        assertThat(entry.getDetails()).isEmpty();
        assertThat(entry.getIp()).isEqualTo("192.168.178.20");
    }

    @Test
    void longTextsAreTruncated() {
        String longText = "x".repeat(2000);
        service.record(AuditEvent.failure(longText, AuditAction.LOGIN_FAILED, longText, longText, longText));

        AuditEntry entry = savedEntry();
        assertThat(entry.getActor()).hasSize(AuditService.MAX_TEXT_LENGTH).endsWith("…");
        assertThat(entry.getSummary()).hasSize(AuditService.MAX_TEXT_LENGTH);
        assertThat(entry.getDetails().get("error")).hasSize(AuditService.MAX_TEXT_LENGTH);
    }

    @Test
    void attemptedUsernamesCannotForgeLogLines(CapturedOutput output) {
        service.record(AuditEvent.failure("x\nAudit LOGIN: user 'admin'", AuditAction.LOGIN_FAILED, "x",
                "Login failed: unknown user", null));

        assertThat(output.getOut()).contains("user 'x Audit LOGIN: user 'admin''").doesNotContain("x\nAudit");
        assertThat(savedEntry().getActor()).isEqualTo("x\nAudit LOGIN: user 'admin'");
    }

    @Test
    void failingDatabaseDoesNotBreakTheAction() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        assertThatCode(() -> service.record(AuditEvent.success("kevin", AuditAction.LOGOUT, "kevin", "Logged out")))
                .doesNotThrowAnyException();
    }

    @Test
    void deduplicatedEventsAreRecordedOncePerInterval() {
        AuditEvent event = AuditEvent.success("kevin", AuditAction.CONTAINER_ENV_VIEW, "minecraft",
                "Viewed the environment variables of container 'minecraft'");

        service.recordDeduplicated(event);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(14)));
        service.recordDeduplicated(event);
        verify(repository, times(1)).save(any());

        // Other users and other targets are recorded separately
        service.recordDeduplicated(AuditEvent.success("alice", event.action(), event.target(), event.summary()));
        service.recordDeduplicated(AuditEvent.success("kevin", event.action(), "satisfactory", "Viewed …"));
        verify(repository, times(3)).save(any());

        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(16)));
        service.recordDeduplicated(event);
        verify(repository, times(4)).save(any());
    }

    @Test
    void searchFiltersSortsAndPages() {
        AuditEntry entry = new AuditEntry();
        entry.setId("e1");
        entry.setActor("kevin");
        entry.setAction(AuditAction.LOGIN);
        when(mongoOperations.count(any(Query.class), eq(AuditEntry.class))).thenReturn(120L);
        when(mongoOperations.find(any(Query.class), eq(AuditEntry.class))).thenReturn(List.of(entry));

        AuditLogFilter filter = new AuditLogFilter(AuditCategory.AUTHENTICATION, "kevin", AuditOutcome.SUCCESS,
                NOW.minus(Duration.ofDays(1)), NOW);
        AuditLogPage page = service.search(filter, 1, 50);

        assertThat(page.entries()).extracting(AuditEntryResponse::id).containsExactly("e1");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(120);
        assertThat(page.totalPages()).isEqualTo(3);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoOperations).find(query.capture(), eq(AuditEntry.class));
        assertThat(query.getValue().getSkip()).isEqualTo(50);
        assertThat(query.getValue().getLimit()).isEqualTo(50);
        assertThat(query.getValue().getSortObject()).isEqualTo(new Document("timestamp", -1).append("_id", -1));
        // Enums are converted by MongoTemplate later; here they are still the enum values
        assertThat(query.getValue().getQueryObject().toString()).contains("category=AUTHENTICATION", "actor=kevin",
                "outcome=SUCCESS", "$gte=" + NOW.minus(Duration.ofDays(1)), "$lt=" + NOW);
    }

    @Test
    void searchWithoutFilterMatchesEverything() {
        service.search(new AuditLogFilter(null, " ", null, null, null), 0, 1000);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoOperations).find(query.capture(), eq(AuditEntry.class));
        assertThat(query.getValue().getQueryObject()).isEmpty();
        assertThat(query.getValue().getLimit()).isEqualTo(AuditService.MAX_PAGE_SIZE);
    }

    @Test
    void actorsAreSortedWithoutBlanks() {
        when(mongoOperations.findDistinct(any(Query.class), eq("actor"), eq(AuditEntry.class), eq(String.class)))
                .thenReturn(List.of("kevin", "", "Alice", "bob"));

        assertThat(service.listActors()).containsExactly("Alice", "bob", "kevin");
        verify(repository, never()).save(any());
    }

    private AuditEntry savedEntry() {
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(entry.capture());
        return entry.getValue();
    }
}
