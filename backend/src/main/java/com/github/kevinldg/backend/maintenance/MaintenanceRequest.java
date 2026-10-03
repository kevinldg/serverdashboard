package com.github.kevinldg.backend.maintenance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceRequest(
        @NotNull
        Boolean enabled,

        @Size(max = MaintenanceService.MAX_MESSAGE_LENGTH)
        String message
) {
}
