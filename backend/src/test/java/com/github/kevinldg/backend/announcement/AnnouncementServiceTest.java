package com.github.kevinldg.backend.announcement;

import com.github.kevinldg.backend.announcement.AnnouncementResponse.Status;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final AnnouncementRepository repository = mock(AnnouncementRepository.class);
    private final AuthenticatedUser admin = new AuthenticatedUser("admin-1", "kevin", null, true, true, 0,
            Set.of(Permission.ANNOUNCEMENT_MANAGE));
    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement announcement = invocation.getArgument(0);
            if (announcement.getId() == null) {
                announcement.setId("new-id");
            }
            return announcement;
        });
    }

    @Test
    void statusDependsOnActiveFlagAndTimeWindow() {
        assertThat(status(true, null, null)).isEqualTo(Status.VISIBLE);
        assertThat(status(false, null, null)).isEqualTo(Status.INACTIVE);
        assertThat(status(true, NOW.plusSeconds(60), null)).isEqualTo(Status.SCHEDULED);
        assertThat(status(true, NOW, null)).as("start time reached").isEqualTo(Status.VISIBLE);
        assertThat(status(true, null, NOW.plusSeconds(1))).isEqualTo(Status.VISIBLE);
        assertThat(status(true, null, NOW)).as("end time reached").isEqualTo(Status.EXPIRED);
        assertThat(status(true, NOW.minusSeconds(3600), NOW.plusSeconds(3600))).isEqualTo(Status.VISIBLE);
        assertThat(status(false, NOW.minusSeconds(3600), NOW.plusSeconds(3600))).isEqualTo(Status.INACTIVE);
    }

    @Test
    void onlyVisibleAnnouncementsAreListedNewestFirst() {
        Announcement older = announcement("older", true, null, null, NOW.minus(Duration.ofDays(2)));
        Announcement newer = announcement("newer", true, null, null, NOW.minus(Duration.ofDays(1)));
        Announcement scheduled = announcement("scheduled", true, NOW.plusSeconds(60), null, NOW);
        Announcement expired = announcement("expired", true, null, NOW.minusSeconds(1), NOW.minus(Duration.ofDays(3)));
        when(repository.findByActiveTrue()).thenReturn(List.of(older, scheduled, newer, expired));

        assertThat(service.listVisible()).extracting(VisibleAnnouncement::title).containsExactly("newer", "older");
    }

    @Test
    void createStoresAuthorAndTrimsText() {
        AnnouncementResponse response = service.create(
                new AnnouncementRequest("  Maintenance  ", "\n Server restart at 22:00.\nBack soon. \n", true, null, null), admin);

        assertThat(response.title()).isEqualTo("Maintenance");
        assertThat(response.message()).isEqualTo("Server restart at 22:00.\nBack soon.");
        assertThat(response.status()).isEqualTo(Status.VISIBLE);
        assertThat(response.createdBy()).isEqualTo("kevin");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        assertThatThrownBy(() -> service.create(
                new AnnouncementRequest("Title", "Message", true, NOW.plusSeconds(60), NOW.plusSeconds(60)), admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void updateCanDeactivateAndRecordsEditor() {
        Announcement existing = announcement("Old", true, null, null, NOW.minusSeconds(60));
        existing.setCreatedBy("someone");
        when(repository.findById("a1")).thenReturn(Optional.of(existing));

        AnnouncementResponse response = service.update("a1", new AnnouncementRequest("New", "Text", false, null, null), admin);

        assertThat(response.status()).isEqualTo(Status.INACTIVE);
        assertThat(response.createdBy()).isEqualTo("someone");
        assertThat(response.updatedBy()).isEqualTo("kevin");
    }

    @Test
    void unknownAnnouncementIsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing", admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Status status(boolean active, Instant startsAt, Instant endsAt) {
        return AnnouncementService.statusOf(announcement("x", active, startsAt, endsAt, NOW), NOW);
    }

    private static Announcement announcement(String title, boolean active, Instant startsAt, Instant endsAt, Instant createdAt) {
        Announcement announcement = new Announcement();
        announcement.setId(title);
        announcement.setTitle(title);
        announcement.setMessage("message");
        announcement.setActive(active);
        announcement.setStartsAt(startsAt);
        announcement.setEndsAt(endsAt);
        announcement.setCreatedAt(createdAt);
        return announcement;
    }
}
