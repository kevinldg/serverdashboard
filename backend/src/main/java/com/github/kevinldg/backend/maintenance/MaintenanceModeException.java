package com.github.kevinldg.backend.maintenance;

/**
 * Thrown when a non-admin request is rejected because maintenance mode is active.
 */
public class MaintenanceModeException extends RuntimeException {

    private final String maintenanceMessage;

    public MaintenanceModeException(String maintenanceMessage) {
        super("The application is currently under maintenance.");
        this.maintenanceMessage = maintenanceMessage;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }
}
