import type { Permission } from "../../api/auth";

/** Admin area tabs, each visible with its permission. Further tabs are added in the following steps. */
export const ADMIN_TABS: { path: string; label: string; permission: Permission }[] = [
    { path: "users", label: "Users", permission: "USER_MANAGE" },
    { path: "roles", label: "Roles & permissions", permission: "ROLE_MANAGE" },
    { path: "announcements", label: "Announcements", permission: "ANNOUNCEMENT_MANAGE" },
    { path: "categories", label: "Categories", permission: "CATEGORY_MANAGE" },
    { path: "maintenance", label: "Maintenance", permission: "MAINTENANCE_MANAGE" },
    { path: "system", label: "System", permission: "SYSTEM_INFO_VIEW" },
    { path: "audit-log", label: "Audit log", permission: "AUDIT_LOG_VIEW" },
];
