import type { ReactNode } from "react";
import type { Permission } from "../api/auth";
import { Alert } from "../components/Alert";
import { hasPermission } from "./permissions";
import { useAuth } from "./useAuth";

/** Renders its children only for users with the permission (the backend enforces it as well). */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
    const { user } = useAuth();
    if (!hasPermission(user, permission)) {
        return <Alert variant="error">You do not have permission to view this page.</Alert>;
    }
    return <>{children}</>;
}
