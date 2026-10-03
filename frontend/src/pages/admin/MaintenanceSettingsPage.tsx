import { useState } from "react";
import { getMaintenanceSettings, MAX_MAINTENANCE_MESSAGE_LENGTH, type MaintenanceStatus, updateMaintenance } from "../../api/maintenance";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ConfirmModal } from "../../components/ConfirmModal";
import { ErrorAlert } from "../../components/ErrorAlert";
import { useApiData } from "../../hooks/useApiData";
import { useMaintenance } from "../../maintenance/useMaintenance";
import { formatDateTime } from "../../utils/format";

export function MaintenanceSettingsPage() {
    const settings = useApiData(getMaintenanceSettings);
    return (
        <div className="flex max-w-2xl flex-col gap-4">
            <div>
                <h2 className="text-lg font-semibold text-white">Maintenance mode</h2>
                <p className="text-sm text-slate-400">
                    While maintenance mode is active, only administrators can log in and use the application.
                    Everyone else sees a maintenance page with the text below.
                </p>
            </div>
            {settings.error !== null && <ErrorAlert error={settings.error} />}
            {/* Remount the form when the stored settings change, so it starts from the saved text */}
            {settings.data && (
                <MaintenanceForm
                    key={`${settings.data.updatedAt}`}
                    settings={settings.data}
                    onSaved={() => void settings.reload()}
                />
            )}
            {!settings.data && settings.loading && <p className="text-slate-400">Loading…</p>}
        </div>
    );
}

function MaintenanceForm({ settings, onSaved }: { settings: MaintenanceStatus; onSaved: () => void }) {
    const { refresh } = useMaintenance();
    const [message, setMessage] = useState(settings.message);
    const [confirmingEnable, setConfirmingEnable] = useState(false);
    const [saving, setSaving] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);
    const [error, setError] = useState<unknown>(null);

    async function save(enabled: boolean, successMessage: string) {
        setConfirmingEnable(false);
        setSaving(true);
        setNotice(null);
        setError(null);
        try {
            await updateMaintenance(enabled, message);
            setNotice(successMessage);
            await refresh();
            onSaved();
        } catch (saveError) {
            setError(saveError);
        } finally {
            setSaving(false);
        }
    }

    return (
        <div className="flex flex-col gap-4">
            {notice && <Alert variant="success">{notice}</Alert>}
            {error !== null && <ErrorAlert error={error} />}

            <div
                className={`rounded-lg border px-5 py-4 ${settings.enabled ? "border-amber-700 bg-amber-950/50" : "border-slate-800 bg-slate-900"}`}
            >
                <p className="font-medium text-white">
                    Maintenance mode is <span className={settings.enabled ? "text-amber-300" : "text-emerald-300"}>
                        {settings.enabled ? "active" : "off"}
                    </span>
                </p>
                {settings.updatedAt && (
                    <p className="mt-1 text-sm text-slate-400">
                        Last changed {formatDateTime(settings.updatedAt)} by {settings.updatedBy}
                    </p>
                )}
            </div>

            <div className="flex flex-col gap-1.5">
                <label htmlFor="maintenance-message" className="text-sm font-medium text-slate-300">
                    Information text for the maintenance page
                </label>
                <textarea
                    id="maintenance-message"
                    value={message}
                    maxLength={MAX_MAINTENANCE_MESSAGE_LENGTH}
                    rows={4}
                    placeholder="e.g. We are updating the server. Expected to be back at 22:00."
                    onChange={(event) => setMessage(event.target.value)}
                    className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500"
                />
                <span className="text-right text-xs text-slate-500">
                    Plain text · {message.length}/{MAX_MAINTENANCE_MESSAGE_LENGTH}
                </span>
            </div>

            <div className="flex flex-wrap gap-3">
                {settings.enabled ? (
                    <>
                        <button
                            type="button"
                            disabled={saving}
                            onClick={() => void save(false, "Maintenance mode was turned off. Everyone can use the application again.")}
                            className={buttonStyles.primary}
                        >
                            Turn off maintenance mode
                        </button>
                        <button
                            type="button"
                            disabled={saving || message === settings.message}
                            onClick={() => void save(true, "The information text was updated.")}
                            className={buttonStyles.secondary}
                        >
                            Update text
                        </button>
                    </>
                ) : (
                    <button type="button" disabled={saving} onClick={() => setConfirmingEnable(true)} className={buttonStyles.danger}>
                        Turn on maintenance mode
                    </button>
                )}
            </div>

            {confirmingEnable && (
                <ConfirmModal
                    title="Turn on maintenance mode?"
                    confirmLabel="Turn on"
                    destructive
                    onCancel={() => setConfirmingEnable(false)}
                    onConfirm={() => void save(true, "Maintenance mode is now active.")}
                >
                    <p>Only administrators will be able to use the application.</p>
                    <p>
                        All other users are blocked immediately, including open sessions and live log streams, and see
                        the maintenance page instead.
                    </p>
                </ConfirmModal>
            )}
        </div>
    );
}
