import type { CurrentUser, Permission } from "../api/auth";

/** Mirrors the backend: admins (superusers) receive every permission. */
export function hasPermission(user: CurrentUser | null, permission: Permission): boolean {
    return user?.permissions.includes(permission) ?? false;
}
