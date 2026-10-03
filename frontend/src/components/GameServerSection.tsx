import { type FormEvent, useState } from "react";
import {
    type ClassificationMode,
    classifyContainer,
    type ContainerDetails,
    type GameServerStatus,
    listGameServerProfiles,
} from "../api/containers";
import { getErrorMessage } from "../api/problem";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { useApiData } from "../hooks/useApiData";
import { Alert } from "./Alert";
import { buttonStyles } from "./buttonStyles";
import { GameServerBadge } from "./GameServerBadge";
import { GAME_SERVER_SOURCE_LABELS } from "./gameServerLabels";
import { Modal } from "./Modal";

const GENERIC = "generic";

const describe = (status: GameServerStatus) =>
    status.gameServer ? `${status.profileName}, ${GAME_SERVER_SOURCE_LABELS[status.source]}` : "not a game server";

/** Game server status of a container, with the option to classify it manually. */
export function GameServerSection({ container, onChanged }: { container: ContainerDetails; onChanged: () => void }) {
    const { user } = useAuth();
    const [editing, setEditing] = useState(false);
    const { gameServer } = container;

    return (
        <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm">
                {gameServer.gameServer ? (
                    <span className="flex items-center gap-2">
                        <GameServerBadge status={gameServer} />
                        <span className="text-slate-400">{GAME_SERVER_SOURCE_LABELS[gameServer.source]}</span>
                    </span>
                ) : (
                    <span className="text-slate-300">
                        Not a game server
                        <span className="ml-2 text-slate-400">
                            ({gameServer.source === "NONE" ? "not detected automatically" : GAME_SERVER_SOURCE_LABELS[gameServer.source]})
                        </span>
                    </span>
                )}
            </div>
            {hasPermission(user, "GAMESERVER_MANAGE") && (
                <button type="button" onClick={() => setEditing(true)} className={buttonStyles.secondary}>
                    Change classification
                </button>
            )}
            {editing && (
                <ClassificationModal
                    container={container}
                    onSaved={() => {
                        setEditing(false);
                        onChanged();
                    }}
                    onClose={() => setEditing(false)}
                />
            )}
        </div>
    );
}

function ClassificationModal({ container, onSaved, onClose }: {
    container: ContainerDetails;
    onSaved: () => void;
    onClose: () => void;
}) {
    const profiles = useApiData(listGameServerProfiles);
    const manual = container.gameServer.source === "MANUAL";
    const [mode, setMode] = useState<ClassificationMode>(
        !manual ? "AUTOMATIC" : container.gameServer.gameServer ? "GAME_SERVER" : "NOT_GAME_SERVER",
    );
    const [profile, setProfile] = useState(
        container.gameServer.profileId ?? container.detectedGameServer.profileId ?? GENERIC,
    );
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setSaving(true);
        try {
            await classifyContainer(container.id, mode, mode === "GAME_SERVER" && profile !== GENERIC ? profile : null);
            onSaved();
        } catch (saveError) {
            setError(getErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal title={`Classify "${container.name}"`} onClose={onClose} size="lg">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4 text-sm">
                {error && <Alert variant="error">{error}</Alert>}
                {profiles.error !== null && <Alert variant="error">{getErrorMessage(profiles.error)}</Alert>}

                <label className="flex items-start gap-3">
                    <input type="radio" className="mt-1" checked={mode === "AUTOMATIC"} onChange={() => setMode("AUTOMATIC")} />
                    <span>
                        <span className="block font-medium text-slate-100">Automatic</span>
                        <span className="text-slate-400">Currently: {describe(container.detectedGameServer)}</span>
                    </span>
                </label>

                <label className="flex items-start gap-3">
                    <input type="radio" className="mt-1" checked={mode === "GAME_SERVER"} onChange={() => setMode("GAME_SERVER")} />
                    <span className="flex flex-1 flex-col gap-2">
                        <span className="font-medium text-slate-100">Game server</span>
                        <select
                            value={profile}
                            disabled={mode !== "GAME_SERVER"}
                            onChange={(event) => setProfile(event.target.value)}
                            aria-label="Game server profile"
                            className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100 disabled:opacity-50"
                        >
                            <option value={GENERIC}>Generic game server (no profile)</option>
                            {profiles.data?.map((option) => (
                                <option key={option.id} value={option.id}>
                                    {option.name}
                                </option>
                            ))}
                        </select>
                    </span>
                </label>

                <label className="flex items-start gap-3">
                    <input type="radio" className="mt-1" checked={mode === "NOT_GAME_SERVER"} onChange={() => setMode("NOT_GAME_SERVER")} />
                    <span className="font-medium text-slate-100">Not a game server</span>
                </label>

                <p className="text-xs text-slate-400">
                    A manual classification is stored by container name, so it is kept when the container is recreated
                    (e.g. after an image update). It takes precedence over automatic detection.
                </p>

                <div className="mt-2 flex justify-end gap-3">
                    <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                        Cancel
                    </button>
                    <button type="submit" disabled={saving} className={buttonStyles.primary}>
                        {saving ? "Saving…" : "Save"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
