import "./monacoSetup";
import { DiffEditor, Editor } from "@monaco-editor/react";
import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { type FileContent, getConfigFileBackup, languageOf, readConfigFile, saveConfigFile } from "../../api/configFiles";
import { restartContainer } from "../../api/containers";
import { getProblem } from "../../api/problem";
import { hasPermission } from "../../auth/permissions";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ConfirmModal } from "../../components/ConfirmModal";
import { ErrorAlert } from "../../components/ErrorAlert";
import { Modal } from "../../components/Modal";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime } from "../../utils/format";

/** Loaded lazily (see App.tsx), so the Monaco editor is only downloaded when a file is opened. */
export default function ConfigFileEditorPage() {
    const { id = "" } = useParams();
    const [searchParams] = useSearchParams();
    const path = searchParams.get("path") ?? "";
    const [loadCount, setLoadCount] = useState(0);
    // Remount to load the file again, e.g. after a conflict
    return <FileLoader key={`${id}:${path}:${loadCount}`} containerId={id} path={path} onReload={() => setLoadCount((count) => count + 1)} />;
}

function FileLoader({ containerId, path, onReload }: { containerId: string; path: string; onReload: () => void }) {
    const file = useApiData(() => readConfigFile(containerId, path));
    const parent = path.slice(0, path.lastIndexOf("/")) || "/";

    return (
        <div className="flex flex-col gap-4">
            <div>
                <Link to={`/containers/${containerId}`} className="text-sm text-sky-400 hover:underline">
                    ← Container
                </Link>
                <span className="mx-2 text-slate-600">·</span>
                <Link to={`/containers/${containerId}/files?path=${encodeURIComponent(parent)}`} className="text-sm text-sky-400 hover:underline">
                    Folder
                </Link>
                <h1 className="mt-2 font-mono text-xl font-semibold text-white">{path}</h1>
            </div>
            {file.error !== null && <ErrorAlert error={file.error} />}
            {!file.data && file.loading && <p className="text-slate-400">Loading…</p>}
            {file.data && <FileEditor containerId={containerId} file={file.data} onReload={onReload} />}
        </div>
    );
}

function FileEditor({ containerId, file, onReload }: { containerId: string; file: FileContent; onReload: () => void }) {
    const { user } = useAuth();
    const canEdit = hasPermission(user, "GAMESERVER_CONFIG_EDIT");
    const canRestart = hasPermission(user, "CONTAINER_RESTART");

    // The saved version the edit is based on
    const [base, setBase] = useState({ content: file.content, sha256: file.sha256 });
    const [value, setValue] = useState(file.content);
    const [backup, setBackup] = useState(file.backup);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState<unknown>(null);
    const [conflict, setConflict] = useState(false);
    const [comparing, setComparing] = useState(false);
    const [confirmRestart, setConfirmRestart] = useState(false);
    const [restartState, setRestartState] = useState<"idle" | "running" | "done">("idle");
    const dirty = value !== base.content;

    // Warn before leaving the page with unsaved changes
    useEffect(() => {
        if (!dirty) return;
        const warn = (event: BeforeUnloadEvent) => event.preventDefault();
        window.addEventListener("beforeunload", warn);
        return () => window.removeEventListener("beforeunload", warn);
    }, [dirty]);

    async function save() {
        setSaving(true);
        setError(null);
        setSaved(false);
        try {
            const result = await saveConfigFile(containerId, file.path, value, base.sha256);
            setBackup({ replacedAt: result.savedAt, replacedBy: user?.username ?? "" });
            setBase({ content: value, sha256: result.sha256 });
            setSaved(true);
            setRestartState("idle");
        } catch (saveError) {
            setConflict(getProblem(saveError)?.status === 409);
            setError(saveError);
        } finally {
            setSaving(false);
        }
    }

    async function restart() {
        setConfirmRestart(false);
        setRestartState("running");
        try {
            await restartContainer(containerId);
            setRestartState("done");
        } catch (restartError) {
            setError(restartError);
            setRestartState("idle");
        }
    }

    return (
        <div className="flex flex-col gap-3">
            {file.hints.map((hint) => (
                <Alert key={hint} variant="info">{hint}</Alert>
            ))}
            {!canEdit && <Alert variant="info">Read-only: you do not have permission to edit configuration files.</Alert>}
            {error !== null && <ErrorAlert error={error} />}
            {conflict && (
                <div className="flex items-center gap-3 text-sm">
                    <button type="button" onClick={onReload} className={buttonStyles.secondary}>
                        Reload the file
                    </button>
                    <span className="text-slate-400">Your unsaved changes will be discarded. Copy them first if you need them.</span>
                </div>
            )}
            {saved && (
                <Alert variant="success">
                    <span>The file was saved. Changes usually take effect after a restart of the container.</span>
                    {canRestart && restartState === "idle" && (
                        <button type="button" onClick={() => setConfirmRestart(true)} className="ml-3 font-medium underline">
                            Restart now
                        </button>
                    )}
                    {restartState === "running" && <span className="ml-3">Restarting…</span>}
                    {restartState === "done" && <span className="ml-3 font-medium">The container was restarted.</span>}
                </Alert>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                <span className="text-slate-400">
                    {dirty ? <span className="text-amber-300">Unsaved changes</span> : "No unsaved changes"}
                    {" · "}last modified {formatDateTime(file.modifiedAt)}
                </span>
                <div className="flex gap-2">
                    {backup && (
                        <button type="button" onClick={() => setComparing(true)} className={buttonStyles.secondary}>
                            Previous version
                        </button>
                    )}
                    {canEdit && (
                        <>
                            <button type="button" disabled={!dirty || saving} onClick={() => setValue(base.content)} className={buttonStyles.secondary}>
                                Discard changes
                            </button>
                            <button type="button" disabled={!dirty || saving} onClick={() => void save()} className={buttonStyles.primary}>
                                {saving ? "Saving…" : "Save"}
                            </button>
                        </>
                    )}
                </div>
            </div>

            <div className="overflow-hidden rounded-md border border-slate-800">
                <Editor
                    height="65vh"
                    theme="vs-dark"
                    language={languageOf(file.path)}
                    value={value}
                    onChange={(newValue) => setValue(newValue ?? "")}
                    options={{ readOnly: !canEdit, minimap: { enabled: false }, fontSize: 13, scrollBeyondLastLine: false }}
                />
            </div>

            {comparing && backup && (
                <PreviousVersionModal
                    containerId={containerId}
                    path={file.path}
                    current={value}
                    canEdit={canEdit}
                    onLoad={(content) => {
                        setValue(content);
                        setComparing(false);
                    }}
                    onClose={() => setComparing(false)}
                />
            )}
            {confirmRestart && (
                <ConfirmModal title="Restart the container?" confirmLabel="Restart" onConfirm={() => void restart()} onCancel={() => setConfirmRestart(false)}>
                    <p>The container will be stopped and started again. Connected players will be disconnected.</p>
                </ConfirmModal>
            )}
        </div>
    );
}

function PreviousVersionModal({ containerId, path, current, canEdit, onLoad, onClose }: {
    containerId: string;
    path: string;
    current: string;
    canEdit: boolean;
    onLoad: (content: string) => void;
    onClose: () => void;
}) {
    const backup = useApiData(() => getConfigFileBackup(containerId, path));

    return (
        <Modal title="Previous version" onClose={onClose} size="xl">
            {backup.error !== null && <ErrorAlert error={backup.error} />}
            {backup.data && (
                <div className="flex flex-col gap-3">
                    <p className="text-sm text-slate-400">
                        Left: version replaced by {backup.data.replacedBy} on {formatDateTime(backup.data.replacedAt)}. Right: the editor's
                        current content.
                    </p>
                    <div className="overflow-hidden rounded-md border border-slate-800">
                        <DiffEditor
                            height="60vh"
                            theme="vs-dark"
                            language={languageOf(path)}
                            original={backup.data.content}
                            modified={current}
                            options={{ readOnly: true, renderSideBySide: true, minimap: { enabled: false } }}
                        />
                    </div>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                            Close
                        </button>
                        {canEdit && (
                            <button type="button" onClick={() => onLoad(backup.data!.content)} className={buttonStyles.primary}>
                                Load previous version into the editor
                            </button>
                        )}
                    </div>
                </div>
            )}
        </Modal>
    );
}
