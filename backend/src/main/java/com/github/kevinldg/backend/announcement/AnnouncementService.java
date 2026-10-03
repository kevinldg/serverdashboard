package com.github.kevinldg.backend.announcement;

import com.github.kevinldg.backend.announcement.AnnouncementResponse.Status;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Dashboard announcements.
 * <p>
 * Visibility is decided when announcements are requested (active, start time reached, end time not reached),
 * so announcements expire automatically without a background job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    static final int MAX_TITLE_LENGTH = 120;
    static final int MAX_MESSAGE_LENGTH = 2000;

    private final AnnouncementRepository announcementRepository;
    private final Clock clock;

    /** Announcements currently shown on the dashboard, newest first. */
    public List<VisibleAnnouncement> listVisible() {
        Instant now = clock.instant();
        return announcementRepository.findByActiveTrue().stream()
                .filter(announcement -> statusOf(announcement, now) == Status.VISIBLE)
                .sorted(Comparator.comparing(AnnouncementService::displayedSince).reversed())
                .map(announcement -> new VisibleAnnouncement(announcement.getId(), announcement.getTitle(),
                        announcement.getMessage(), announcement.getStartsAt(), announcement.getEndsAt()))
                .toList();
    }

    /** All announcements for the management, newest first. */
    public List<AnnouncementResponse> listAll() {
        Instant now = clock.instant();
        return announcementRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(announcement -> toResponse(announcement, now))
                .toList();
    }

    public AnnouncementResponse create(AnnouncementRequest request, AuthenticatedUser actor) {
        validatePeriod(request);
        Instant now = clock.instant();

        Announcement announcement = new Announcement();
        apply(request, announcement);
        announcement.setCreatedAt(now);
        announcement.setCreatedBy(actor.getUsername());
        announcement.setUpdatedAt(now);
        announcement.setUpdatedBy(actor.getUsername());
        Announcement saved = announcementRepository.save(announcement);

        log.info("User '{}' created announcement '{}'", actor.getUsername(), saved.getTitle());
        return toResponse(saved, now);
    }

    public AnnouncementResponse update(String id, AnnouncementRequest request, AuthenticatedUser actor) {
        validatePeriod(request);
        Announcement announcement = find(id);
        Instant now = clock.instant();

        apply(request, announcement);
        announcement.setUpdatedAt(now);
        announcement.setUpdatedBy(actor.getUsername());
        Announcement saved = announcementRepository.save(announcement);

        log.info("User '{}' updated announcement '{}' ({})", actor.getUsername(), saved.getTitle(),
                saved.isActive() ? "active" : "inactive");
        return toResponse(saved, now);
    }

    public void delete(String id, AuthenticatedUser actor) {
        Announcement announcement = find(id);
        announcementRepository.delete(announcement);
        log.info("User '{}' deleted announcement '{}'", actor.getUsername(), announcement.getTitle());
    }

    private static void validatePeriod(AnnouncementRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end time must be after the start time.");
        }
    }

    private static void apply(AnnouncementRequest request, Announcement announcement) {
        announcement.setTitle(request.title().trim());
        announcement.setMessage(request.message().strip());
        announcement.setActive(request.active());
        announcement.setStartsAt(request.startsAt());
        announcement.setEndsAt(request.endsAt());
    }

    static Status statusOf(Announcement announcement, Instant now) {
        if (!announcement.isActive()) {
            return Status.INACTIVE;
        }
        if (announcement.getStartsAt() != null && now.isBefore(announcement.getStartsAt())) {
            return Status.SCHEDULED;
        }
        if (announcement.getEndsAt() != null && !now.isBefore(announcement.getEndsAt())) {
            return Status.EXPIRED;
        }
        return Status.VISIBLE;
    }

    private static Instant displayedSince(Announcement announcement) {
        return announcement.getStartsAt() != null ? announcement.getStartsAt() : announcement.getCreatedAt();
    }

    private Announcement find(String id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The announcement does not exist (anymore)."));
    }

    private static AnnouncementResponse toResponse(Announcement announcement, Instant now) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getMessage(),
                announcement.isActive(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                statusOf(announcement, now),
                announcement.getCreatedAt(),
                announcement.getCreatedBy(),
                announcement.getUpdatedAt(),
                announcement.getUpdatedBy()
        );
    }
}
