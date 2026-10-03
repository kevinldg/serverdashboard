import { apiClient } from "./client";

export interface ManagedUser {
    id: string;
    username: string;
    /** Null if the user's role no longer exists. */
    role: { id: string; name: string } | null;
    admin: boolean;
    active: boolean;
    passwordChangeRecommended: boolean;
    createdAt: string | null;
    lastLoginAt: string | null;
}

export interface RoleOption {
    id: string;
    name: string;
    /** Admin roles can only be assigned by administrators. */
    admin: boolean;
}

/** Must match the backend validation (UserManagementService). */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,32}$/;

export async function listUsers(): Promise<ManagedUser[]> {
    const response = await apiClient.get<ManagedUser[]>("/admin/users");
    return response.data;
}

export async function listRoleOptions(): Promise<RoleOption[]> {
    const response = await apiClient.get<RoleOption[]>("/admin/users/role-options");
    return response.data;
}

/**
 * Creates a user. Without a password, a random one is generated and returned once as `generatedPassword`.
 */
export async function createUser(username: string, roleId: string, password: string | null) {
    const response = await apiClient.post<{ user: ManagedUser; generatedPassword: string | null }>("/admin/users", {
        username,
        roleId,
        password,
    });
    return response.data;
}

export async function updateUser(id: string, username: string, roleId: string, active: boolean): Promise<ManagedUser> {
    const response = await apiClient.put<ManagedUser>(`/admin/users/${encodeURIComponent(id)}`, { username, roleId, active });
    return response.data;
}

/** Generates a new password (returned once) and logs the user out everywhere. */
export async function resetPassword(id: string): Promise<{ username: string; generatedPassword: string }> {
    const response = await apiClient.post<{ username: string; generatedPassword: string }>(
        `/admin/users/${encodeURIComponent(id)}/reset-password`,
    );
    return response.data;
}

export async function deleteUser(id: string): Promise<void> {
    await apiClient.delete(`/admin/users/${encodeURIComponent(id)}`);
}
