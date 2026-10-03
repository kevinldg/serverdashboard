package com.github.kevinldg.backend.maintenance;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Maintenance mode: while enabled, only administrators can use the application.
 * <p>
 * The state is checked on every request, so it is kept in memory and loaded from MongoDB once.
 * This works because there is exactly one backend instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    static final int MAX_MESSAGE_LENGTH = 1000;

    private final MaintenanceSettingsRepository repository;
    private final Clock clock;

    private volatile MaintenanceStatus cachedStatus;

    public MaintenanceStatus getStatus() {
        MaintenanceStatus status = cachedStatus;
        if (status == null) {
            synchronized (this) {
                if (cachedStatus == null) {
                    cachedStatus = repository.findById(MaintenanceSettings.ID)
                            .map(MaintenanceService::toStatus)
                            .orElse(new MaintenanceStatus(false, "", null, null));
                }
                status = cachedStatus;
            }
        }
        return status;
    }

    public boolean isEnabled() {
        return getStatus().enabled();
    }

    /** Whether the user may use the application right now (always, unless maintenance is on and they are no admin). */
    public boolean allows(AuthenticatedUser user) {
        return !isEnabled() || (user != null && user.isAdmin());
    }

    public synchronized MaintenanceStatus update(MaintenanceRequest request, AuthenticatedUser actor) {
        MaintenanceSettings settings = repository.findById(MaintenanceSettings.ID).orElseGet(MaintenanceSettings::new);
        settings.setEnabled(request.enabled());
        settings.setMessage(request.message() == null ? "" : request.message().strip());
        settings.setUpdatedAt(clock.instant());
        settings.setUpdatedBy(actor.getUsername());
        cachedStatus = toStatus(repository.save(settings));

        log.info("User '{}' {} maintenance mode", actor.getUsername(), request.enabled() ? "enabled" : "disabled");
        return cachedStatus;
    }

    private static MaintenanceStatus toStatus(MaintenanceSettings settings) {
        return new MaintenanceStatus(settings.isEnabled(), settings.getMessage() == null ? "" : settings.getMessage(),
                settings.getUpdatedAt(), settings.getUpdatedBy());
    }
}
