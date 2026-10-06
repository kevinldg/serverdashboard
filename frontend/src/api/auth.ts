import { apiClient } from "./client";

/** Must match the backend validation (PasswordPolicy). */
export const MIN_PASSWORD_LENGTH = 12;

export type Permission =
    | "DASHBOARD_VIEW"
    | "CONTAINER_VIEW"
    | "CONTAINER_LOGS_VIEW"
    | "CONTAINER_ENV_VIEW"
    | "CONTAINER_START"
    | "CONTAINER_STOP"
    | "CONTAINER_RESTART"
    | "CONTAINER_FORCE_STOP"
    | "CONTAINER_CREATE"
    | "CONTAINER_DELETE"
    | "GAMESERVER_MANAGE"
    | "GAMESERVER_CONFIG_VIEW"
    | "GAMESERVER_CONFIG_EDIT"
    | "USER_MANAGE"
    | "ROLE_MANAGE"
    | "ANNOUNCEMENT_MANAGE"
    | "MAINTENANCE_MANAGE"
    | "SYSTEM_INFO_VIEW"
    | "AUDIT_LOG_VIEW";

export interface CurrentUser {
    id: string;
    username: string;
    role: { id: string; name: string } | null;
    admin: boolean;
    permissions: Permission[];
    passwordChangeRecommended: boolean;
}

export async function login(username: string, password: string): Promise<CurrentUser> {
    // Make sure the CSRF cookie exists before the first state-changing request.
    await apiClient.get("/auth/csrf");
    const response = await apiClient.post<CurrentUser>("/auth/login", new URLSearchParams({ username, password }));
    return response.data;
}

export async function logout(): Promise<void> {
    await apiClient.post("/auth/logout");
}

export async function getCurrentUser(): Promise<CurrentUser> {
    const response = await apiClient.get<CurrentUser>("/auth/me");
    return response.data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await apiClient.put("/auth/password", { currentPassword, newPassword });
}
