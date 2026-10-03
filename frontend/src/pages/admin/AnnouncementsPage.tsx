import { type FormEvent, useState } from "react";
import {
    type AnnouncementInput,
    type AnnouncementStatus,
    createAnnouncement,
    deleteAnnouncement,
    listAnnouncements,
    type ManagedAnnouncement,
    MAX_MESSAGE_LENGTH,
    MAX_TITLE_LENGTH,
    updateAnnouncement,
} from "../../api/announcements";
import { getErrorMessage, getFieldErrors } from "../../api/problem";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ConfirmModal } from "../../components/ConfirmModal";
import { ErrorAlert } from "../../components/ErrorAlert";
import { Modal } from "../../components/Modal";
import { RefreshButton } from "../../components/RefreshButton";
import { RowButton } from "../../components/RowButton";
import { TextField } from "../../components/TextField";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime, fromDateTimeLocal, toDateTimeLocal } from "../../utils/format";

type Dialog = { type: "create" } | { type: "edit"; announcement: ManagedAnnouncement } | { type: "delete"; announcement: ManagedAnnouncement };

const STATUS_STYLES: Record<AnnouncementStatus, { label: string; className: string }> = {
    VISIBLE: { label: "visible", className: "bg-success-soft text-success-fg ring-success-line" },
    SCHEDULED: { label: "scheduled", className: "bg-accent-soft text-accent-fg ring-accent-line" },
    EXPIRED: { label: "expired", className: "bg-raised text-fg-muted ring-line-strong" },
    INACTIVE: { label: "inactive", className: "bg-raised text-fg-secondary ring-line-strong" },
};

const toInput = (announcement: ManagedAnnouncement, active: boolean): AnnouncementInput => ({
    title: announcement.title,
    message: announcement.message,
    active,
    startsAt: announcement.startsAt,
    endsAt: announcement.endsAt,
});

export function AnnouncementsPage() {
    const announcements = useApiData(listAnnouncements);
    const [dialog, setDialog] = useState<Dialog | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [actionError, setActionError] = useState<unknown>(null);

    async function runAction(action: () => Promise<string>) {
        setDialog(null);
        setNotice(null);
        setActionError(null);
        try {
            setNotice(await action());
        } catch (error) {
            setActionError(error);
        }
        await announcements.reload();
    }

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <h2 className="text-lg font-semibold text-fg-strong">Announcements</h2>
                    <p className="text-sm text-fg-muted">
                        Active announcements are shown on the dashboard within their optional time window.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <RefreshButton
                        onRefresh={() => void announcements.reload()}
                        loading={announcements.loading}
                        lastUpdated={announcements.lastUpdated}
                    />
                    <button type="button" onClick={() => setDialog({ type: "create" })} className={buttonStyles.primary}>
                        Create announcement
                    </button>
                </div>
            </div>

            {notice && <Alert variant="success">{notice}</Alert>}
            {actionError !== null && <ErrorAlert error={actionError} />}
            {announcements.error !== null && <ErrorAlert error={announcements.error} />}

            {announcements.data && (
                <div className="overflow-x-auto rounded-lg border border-line">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                            <tr>
                                <th className="px-4 py-3 font-medium">Title</th>
                                <th className="px-4 py-3 font-medium">Status</th>
                                <th className="px-4 py-3 font-medium">Shown from</th>
                                <th className="px-4 py-3 font-medium">Shown until</th>
                                <th className="px-4 py-3 font-medium">Last changed</th>
                                <th className="px-4 py-3 font-medium">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {announcements.data.map((announcement) => (
                                <tr key={announcement.id} className="hover:bg-surface/60">
                                    <td className="max-w-xs px-4 py-3">
                                        <span className="font-medium">{announcement.title}</span>
                                        <p className="truncate text-xs text-fg-muted" title={announcement.message}>
                                            {announcement.message}
                                        </p>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span
                                            className={`rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${STATUS_STYLES[announcement.status].className}`}
                                        >
                                            {STATUS_STYLES[announcement.status].label}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-fg-muted">
                                        {announcement.startsAt ? formatDateTime(announcement.startsAt) : "immediately"}
                                    </td>
                                    <td className="px-4 py-3 text-fg-muted">
                                        {announcement.endsAt ? formatDateTime(announcement.endsAt) : "no end"}
                                    </td>
                                    <td className="px-4 py-3 text-fg-muted">
                                        {formatDateTime(announcement.updatedAt)}
                                        <span className="block text-xs">by {announcement.updatedBy}</span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <div className="flex flex-wrap gap-2">
                                            <RowButton onClick={() => setDialog({ type: "edit", announcement })}>Edit</RowButton>
                                            <RowButton
                                                onClick={() =>
                                                    void runAction(async () => {
                                                        await updateAnnouncement(announcement.id, toInput(announcement, !announcement.active));
                                                        return `"${announcement.title}" was ${announcement.active ? "deactivated" : "activated"}.`;
                                                    })
                                                }
                                            >
                                                {announcement.active ? "Deactivate" : "Activate"}
                                            </RowButton>
                                            <RowButton danger onClick={() => setDialog({ type: "delete", announcement })}>
                                                Delete
                                            </RowButton>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {announcements.data.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="px-4 py-6 text-center text-fg-muted">
                                        No announcements yet.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}
            {!announcements.data && announcements.loading && <p className="text-fg-muted">Loading announcements…</p>}

            {(dialog?.type === "create" || dialog?.type === "edit") && (
                <AnnouncementFormModal
                    announcement={dialog.type === "edit" ? dialog.announcement : null}
                    onSaved={(message) => {
                        setDialog(null);
                        setNotice(message);
                        setActionError(null);
                        void announcements.reload();
                    }}
                    onClose={() => setDialog(null)}
                />
            )}

            {dialog?.type === "delete" && (
                <ConfirmModal
                    title={`Delete announcement "${dialog.announcement.title}"?`}
                    confirmLabel="Delete announcement"
                    destructive
                    onCancel={() => setDialog(null)}
                    onConfirm={() => {
                        const target = dialog.announcement;
                        void runAction(async () => {
                            await deleteAnnouncement(target.id);
                            return `"${target.title}" was deleted.`;
                        });
                    }}
                >
                    <p>The announcement will be permanently deleted. This cannot be undone.</p>
                    <p className="text-fg-muted">To hide it temporarily instead, deactivate it.</p>
                </ConfirmModal>
            )}
        </div>
    );
}

interface AnnouncementFormModalProps {
    /** Null to create a new announcement. */
    announcement: ManagedAnnouncement | null;
    onSaved: (message: string) => void;
    onClose: () => void;
}

function AnnouncementFormModal({ announcement, onSaved, onClose }: AnnouncementFormModalProps) {
    const isNew = announcement === null;
    const [title, setTitle] = useState(announcement?.title ?? "");
    const [message, setMessage] = useState(announcement?.message ?? "");
    const [active, setActive] = useState(announcement?.active ?? true);
    const [startsAt, setStartsAt] = useState(toDateTimeLocal(announcement?.startsAt ?? null));
    const [endsAt, setEndsAt] = useState(toDateTimeLocal(announcement?.endsAt ?? null));
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);

        const validationErrors: Record<string, string> = {};
        if (!title.trim()) validationErrors.title = "Please enter a title.";
        if (!message.trim()) validationErrors.message = "Please enter a message.";
        if (startsAt && endsAt && new Date(endsAt) <= new Date(startsAt)) {
            validationErrors.endsAt = "The end time must be after the start time.";
        }
        setFieldErrors(validationErrors);
        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        const input: AnnouncementInput = {
            title: title.trim(),
            message: message.trim(),
            active,
            startsAt: fromDateTimeLocal(startsAt),
            endsAt: fromDateTimeLocal(endsAt),
        };
        setSaving(true);
        try {
            const saved = isNew ? await createAnnouncement(input) : await updateAnnouncement(announcement.id, input);
            onSaved(`"${saved.title}" was ${isNew ? "created" : "updated"}.`);
        } catch (saveError) {
            setFieldErrors(getFieldErrors(saveError));
            setError(getErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal title={isNew ? "Create announcement" : "Edit announcement"} onClose={onClose} size="lg">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                {error && <Alert variant="error">{error}</Alert>}

                <TextField
                    label="Title"
                    value={title}
                    maxLength={MAX_TITLE_LENGTH}
                    onChange={(event) => setTitle(event.target.value)}
                    error={fieldErrors.title}
                    required
                />

                <div className="flex flex-col gap-1.5">
                    <label htmlFor="announcement-message" className="text-sm font-medium text-fg-secondary">
                        Message
                    </label>
                    <textarea
                        id="announcement-message"
                        value={message}
                        maxLength={MAX_MESSAGE_LENGTH}
                        rows={5}
                        onChange={(event) => setMessage(event.target.value)}
                        aria-invalid={fieldErrors.message ? true : undefined}
                        className="rounded-md border border-line-strong bg-surface px-3 py-2 text-fg outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 aria-invalid:border-red-500"
                    />
                    <div className="flex justify-between text-xs">
                        <span className="text-danger-fg-vivid">{fieldErrors.message}</span>
                        <span className="text-fg-subtle">
                            Plain text, line breaks are kept · {message.length}/{MAX_MESSAGE_LENGTH}
                        </span>
                    </div>
                </div>

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <DateTimeField label="Show from (optional)" value={startsAt} onChange={setStartsAt} error={fieldErrors.startsAt} />
                    <DateTimeField label="Show until (optional)" value={endsAt} onChange={setEndsAt} error={fieldErrors.endsAt} />
                </div>
                <p className="-mt-2 text-xs text-fg-muted">
                    Times are in your browser's time zone. Without an end time, the announcement stays until it is deactivated.
                </p>

                <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
                    Active
                </label>

                <div className="mt-2 flex justify-end gap-3">
                    <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                        Cancel
                    </button>
                    <button type="submit" disabled={saving} className={buttonStyles.primary}>
                        {saving ? "Saving…" : isNew ? "Create announcement" : "Save"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}

function DateTimeField({ label, value, onChange, error }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    error?: string;
}) {
    return (
        <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-fg-secondary">{label}</span>
            <div className="flex gap-2">
                <input
                    type="datetime-local"
                    aria-label={label}
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    aria-invalid={error ? true : undefined}
                    className="w-full rounded-md border border-line-strong bg-surface px-3 py-2 text-fg aria-invalid:border-red-500"
                />
                {value && (
                    <button type="button" onClick={() => onChange("")} className="text-xs text-accent-fg-vivid hover:underline">
                        Clear
                    </button>
                )}
            </div>
            {error && <p className="text-sm text-danger-fg-vivid">{error}</p>}
        </div>
    );
}
