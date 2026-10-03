import type { Permission } from "./auth";
import { apiClient } from "./client";

export type PermissionGroup = "DASHBOARD" | "CONTAINERS" | "GAME_SERVERS" | "ADMINISTRATION";

export const PERMISSION_GROUP_LABELS: Record<PermissionGroup, string> = {
    DASHBOARD: "Dashboard",
    CONTAINERS: "Containers",
    GAME_SERVERS: "Game servers",
    ADMINISTRATION: "Administration",
};

export interface PermissionInfo {
    name: Permission;
    group: PermissionGroup;
    description: string;
}

export interface ManagedRole {
    id: string;
    name: string;
    /** Built-in roles cannot be renamed or deleted. */
    builtIn: boolean;
    /** The Admin role has every permission and cannot be changed. */
    admin: boolean;
    permissions: Permission[];
    userCount: number;
}

export async function listRoles(): Promise<ManagedRole[]> {
    const response = await apiClient.get<ManagedRole[]>("/admin/roles");
    return response.data;
}

export async function listPermissions(): Promise<PermissionInfo[]> {
    const response = await apiClient.get<PermissionInfo[]>("/admin/permissions");
    return response.data;
}

export async function createRole(name: string, permissions: Permission[]): Promise<ManagedRole> {
    const response = await apiClient.post<ManagedRole>("/admin/roles", { name, permissions });
    return response.data;
}

export async function updateRole(id: string, name: string, permissions: Permission[]): Promise<ManagedRole> {
    const response = await apiClient.put<ManagedRole>(`/admin/roles/${encodeURIComponent(id)}`, { name, permissions });
    return response.data;
}

export async function deleteRole(id: string): Promise<void> {
    await apiClient.delete(`/admin/roles/${encodeURIComponent(id)}`);
}
