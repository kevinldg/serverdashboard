const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" });
const timeFormat = new Intl.DateTimeFormat(undefined, { timeStyle: "medium" });

export function formatDateTime(value: string | Date | null): string {
    return value ? dateTimeFormat.format(new Date(value)) : "–";
}

export function formatTime(value: Date): string {
    return timeFormat.format(value);
}

/** Formats a duration as e.g. "3d 4h", "2h 15m" or "45s". */
export function formatDuration(milliseconds: number): string {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    if (minutes > 0) return `${minutes}m ${seconds}s`;
    return `${seconds}s`;
}

/** Converts an ISO timestamp to the value of a `datetime-local` input (browser time zone). */
export function toDateTimeLocal(value: string | null): string {
    if (!value) return "";
    const date = new Date(value);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Converts the value of a `datetime-local` input (browser time zone) to an ISO timestamp, or null if empty. */
export function fromDateTimeLocal(value: string): string | null {
    return value ? new Date(value).toISOString() : null;
}
