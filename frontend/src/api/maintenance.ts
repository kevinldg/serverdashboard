import { apiClient } from "./client";

/** Must match the backend validation (MaintenanceService). */
export const MAX_MAINTENANCE_MESSAGE_LENGTH = 1000;

export interface PublicMaintenanceStatus {
    enabled: boolean;
    /** Information text for the maintenance page; may be empty. */
    message: string;
}

export interface MaintenanceStatus extends PublicMaintenanceStatus {
    updatedAt: string | null;
    updatedBy: string | null;
}

/** Public: available without login. */
export async function getMaintenanceStatus(): Promise<PublicMaintenanceStatus> {
    const response = await apiClient.get<PublicMaintenanceStatus>("/maintenance");
    return response.data;
}

export async function getMaintenanceSettings(): Promise<MaintenanceStatus> {
    const response = await apiClient.get<MaintenanceStatus>("/admin/maintenance");
    return response.data;
}

export async function updateMaintenance(enabled: boolean, message: string): Promise<MaintenanceStatus> {
    const response = await apiClient.put<MaintenanceStatus>("/admin/maintenance", { enabled, message });
    return response.data;
}
