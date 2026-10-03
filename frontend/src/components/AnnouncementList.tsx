import type { VisibleAnnouncement } from "../api/announcements";

/** Announcements shown on the dashboard. Messages are plain text; line breaks are preserved. */
export function AnnouncementList({ announcements }: { announcements: VisibleAnnouncement[] }) {
    if (announcements.length === 0) {
        return null;
    }
    return (
        <div className="flex flex-col gap-3">
            {announcements.map((announcement) => (
                <article
                    key={announcement.id}
                    className="rounded-lg border border-accent-line bg-accent-soft/40 px-5 py-4"
                    aria-label={`Announcement: ${announcement.title}`}
                >
                    <h2 className="font-semibold text-accent-fg-strong">{announcement.title}</h2>
                    <p className="mt-1 whitespace-pre-line text-sm text-accent-fg-strong/90">{announcement.message}</p>
                </article>
            ))}
        </div>
    );
}
