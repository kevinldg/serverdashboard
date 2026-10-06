package com.github.kevinldg.backend.maintenance;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Maintenance mode: while enabled, only administrators can use the application.
 * <p>
 * The state is checked on every request, so it is kept in memory and loaded from MongoDB once.
 * This works because there is exactly one backend instance.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    static final int MAX_MESSAGE_LENGTH = 1000;

    private final MaintenanceSettingsRepository repository;
    private final AuditService auditService;
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
        boolean wasEnabled = isEnabled();
        MaintenanceSettings settings = repository.findById(MaintenanceSettings.ID).orElseGet(MaintenanceSettings::new);
        settings.setEnabled(request.enabled());
        settings.setMessage(request.message() == null ? "" : request.message().strip());
        settings.setUpdatedAt(clock.instant());
        settings.setUpdatedBy(actor.getUsername());
        cachedStatus = toStatus(repository.save(settings));

        auditService.record(AuditEvent.success(actor.getUsername(),
                        request.enabled() ? AuditAction.MAINTENANCE_ENABLE : AuditAction.MAINTENANCE_DISABLE, null,
                        summary(wasEnabled, request.enabled()))
                .detail("message", cachedStatus.message().isEmpty() ? null : cachedStatus.message()));
        return cachedStatus;
    }

    private static String summary(boolean wasEnabled, boolean enabled) {
        if (wasEnabled == enabled) {
            return enabled ? "Changed the maintenance mode text" : "Saved the maintenance settings (maintenance mode stays off)";
        }
        return enabled ? "Enabled maintenance mode" : "Disabled maintenance mode";
    }

    private static MaintenanceStatus toStatus(MaintenanceSettings settings) {
        return new MaintenanceStatus(settings.isEnabled(), settings.getMessage() == null ? "" : settings.getMessage(),
                settings.getUpdatedAt(), settings.getUpdatedBy());
    }
}
