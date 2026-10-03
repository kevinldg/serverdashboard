import { apiClient } from "./client";

/** Must match the backend validation (AnnouncementService). */
export const MAX_TITLE_LENGTH = 120;
export const MAX_MESSAGE_LENGTH = 2000;

export interface VisibleAnnouncement {
    id: string;
    title: string;
    /** Plain text; line breaks are meaningful. */
    message: string;
    startsAt: string | null;
    endsAt: string | null;
}

export type AnnouncementStatus = "VISIBLE" | "SCHEDULED" | "EXPIRED" | "INACTIVE";

export interface ManagedAnnouncement extends VisibleAnnouncement {
    active: boolean;
    status: AnnouncementStatus;
    createdAt: string;
    createdBy: string;
    updatedAt: string;
    updatedBy: string;
}

export interface AnnouncementInput {
    title: string;
    message: string;
    active: boolean;
    /** ISO timestamps or null */
    startsAt: string | null;
    endsAt: string | null;
}

export async function listVisibleAnnouncements(): Promise<VisibleAnnouncement[]> {
    const response = await apiClient.get<VisibleAnnouncement[]>("/announcements");
    return response.data;
}

export async function listAnnouncements(): Promise<ManagedAnnouncement[]> {
    const response = await apiClient.get<ManagedAnnouncement[]>("/admin/announcements");
    return response.data;
}

export async function createAnnouncement(input: AnnouncementInput): Promise<ManagedAnnouncement> {
    const response = await apiClient.post<ManagedAnnouncement>("/admin/announcements", input);
    return response.data;
}

export async function updateAnnouncement(id: string, input: AnnouncementInput): Promise<ManagedAnnouncement> {
    const response = await apiClient.put<ManagedAnnouncement>(`/admin/announcements/${encodeURIComponent(id)}`, input);
    return response.data;
}

export async function deleteAnnouncement(id: string): Promise<void> {
    await apiClient.delete(`/admin/announcements/${encodeURIComponent(id)}`);
}
