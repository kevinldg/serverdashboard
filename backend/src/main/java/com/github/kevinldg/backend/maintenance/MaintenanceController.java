package com.github.kevinldg.backend.maintenance;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    /** Public: the frontend shows the maintenance page without login. */
    @GetMapping("/api/maintenance")
    public MaintenanceStatus.PublicStatus getPublicStatus() {
        return maintenanceService.getStatus().toPublic();
    }

    @GetMapping("/api/admin/maintenance")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceStatus getStatus() {
        return maintenanceService.getStatus();
    }

    @PutMapping("/api/admin/maintenance")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceStatus update(@Valid @RequestBody MaintenanceRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return maintenanceService.update(request, actor);
    }
}
