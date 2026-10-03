package com.github.kevinldg.backend.maintenance;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final MaintenanceSettingsRepository repository = mock(MaintenanceSettingsRepository.class);
    private final AuthenticatedUser admin = new AuthenticatedUser("a", "kevin", null, true, true, 0, Set.of());
    private final AuthenticatedUser user = new AuthenticatedUser("u", "alice", null, true, false, 0, Set.of());
    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(MaintenanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void disabledByDefault() {
        when(repository.findById(MaintenanceSettings.ID)).thenReturn(Optional.empty());

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.allows(user)).isTrue();
        assertThat(service.allows(null)).isTrue();
    }

    @Test
    void stateIsLoadedOnceAndCached() {
        when(repository.findById(MaintenanceSettings.ID)).thenReturn(Optional.empty());

        service.isEnabled();
        service.isEnabled();
        service.getStatus();

        verify(repository, times(1)).findById(MaintenanceSettings.ID);
    }

    @Test
    void enablingBlocksEveryoneButAdmins() {
        when(repository.findById(MaintenanceSettings.ID)).thenReturn(Optional.empty());

        MaintenanceStatus status = service.update(new MaintenanceRequest(true, "  Back at 22:00.  "), admin);

        assertThat(status.enabled()).isTrue();
        assertThat(status.message()).isEqualTo("Back at 22:00.");
        assertThat(status.updatedBy()).isEqualTo("kevin");
        assertThat(status.updatedAt()).isEqualTo(NOW);
        assertThat(service.allows(admin)).isTrue();
        assertThat(service.allows(user)).isFalse();
        assertThat(service.allows(null)).isFalse();
    }

    @Test
    void storedStateIsUsedAfterRestart() {
        MaintenanceSettings stored = new MaintenanceSettings();
        stored.setEnabled(true);
        stored.setMessage("Upgrading");
        when(repository.findById(MaintenanceSettings.ID)).thenReturn(Optional.of(stored));

        assertThat(service.getStatus().toPublic()).isEqualTo(new MaintenanceStatus.PublicStatus(true, "Upgrading"));
    }
}
