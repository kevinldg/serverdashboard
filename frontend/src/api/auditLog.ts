import { apiClient } from "./client";

export type AuditCategory =
    | "AUTHENTICATION"
    | "CONTAINER"
    | "GAME_SERVER"
    | "CONFIG_FILE"
    | "USER"
    | "ROLE"
    | "ANNOUNCEMENT"
    | "MAINTENANCE"
    | "SENSITIVE_READ"
    | "ACCESS";

export type AuditOutcome = "SUCCESS" | "FAILURE" | "DENIED";

export interface AuditEntry {
    id: string;
    timestamp: string;
    /** Username at the time of the action; for failed logins the attempted username. */
    actor: string;
    category: AuditCategory;
    /** e.g. "CONTAINER_STOP" */
    action: string;
    outcome: AuditOutcome;
    target: string | null;
    summary: string;
    details: Record<string, string>;
    /** Only set for login and logout events. */
    ip: string | null;
}

export interface AuditLogPage {
    entries: AuditEntry[];
    /** Zero-based */
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface AuditLogQuery {
    page: number;
    size: number;
    category: AuditCategory | null;
    actor: string | null;
    outcome: AuditOutcome | null;
    /** ISO timestamps; `from` is inclusive, `to` exclusive. */
    from: string | null;
    to: string | null;
}

export async function searchAuditLog(query: AuditLogQuery): Promise<AuditLogPage> {
    // Empty filters are left out (axios skips null values)
    const response = await apiClient.get<AuditLogPage>("/admin/audit-log", { params: query });
    return response.data;
}

export async function listAuditActors(): Promise<string[]> {
    const response = await apiClient.get<string[]>("/admin/audit-log/actors");
    return response.data;
}
