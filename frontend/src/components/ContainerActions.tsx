import { type ReactNode, useState } from "react";
import type { Permission } from "../api/auth";
import {
    type ContainerState,
    deleteContainer,
    forceStopContainer,
    type MountInfo,
    restartContainer,
    startContainer,
    STOPPED_STATES,
    stopContainer,
} from "../api/containers";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { ConfirmModal } from "./ConfirmModal";

type Action = "start" | "stop" | "restart" | "forceStop" | "delete";

interface ActionDefinition {
    label: string;
    pendingLabel: string;
    permission: Permission;
    run: (id: string) => Promise<void>;
    /** Whether the action applies to a container in this state. */
    appliesTo: (state: ContainerState | null) => boolean;
    destructive?: boolean;
}

const isStopped = (state: ContainerState | null) => state !== null && STOPPED_STATES.includes(state);
const isActive = (state: ContainerState | null) => state === "running" || state === "paused" || state === "restarting";

const ACTIONS: Record<Action, ActionDefinition> = {
    start: { label: "Start", pendingLabel: "Starting…", permission: "CONTAINER_START", run: startContainer, appliesTo: isStopped },
    stop: { label: "Stop", pendingLabel: "Stopping…", permission: "CONTAINER_STOP", run: stopContainer, appliesTo: isActive },
    restart: {
        label: "Restart",
        pendingLabel: "Restarting…",
        permission: "CONTAINER_RESTART",
        run: restartContainer,
        appliesTo: (state) => state === "running" || state === "paused",
    },
    forceStop: {
        label: "Force stop",
        pendingLabel: "Stopping…",
        permission: "CONTAINER_FORCE_STOP",
        run: forceStopContainer,
        appliesTo: isActive,
        destructive: true,
    },
    delete: {
        label: "Delete",
        pendingLabel: "Deleting…",
        permission: "CONTAINER_DELETE",
        run: deleteContainer,
        // Shown for every state; only enabled for stopped containers (see below).
        appliesTo: () => true,
        destructive: true,
    },
};

interface ContainerActionsProps {
    container: { id: string; name: string; state: ContainerState | null; dashboard: boolean };
    /** Needed for the delete confirmation, which lists the data that remains. */
    mounts?: MountInfo[];
    /** "row": start/stop/restart for the dashboard table; "full": all actions for the details page. */
    variant: "row" | "full";
    onCompleted: () => void;
    onDeleted?: () => void;
    /** Called with the error of a failed action, or null when a new action starts. */
    onError: (error: unknown) => void;
}

export function ContainerActions({ container, mounts = [], variant, onCompleted, onDeleted, onError }: ContainerActionsProps) {
    const { user } = useAuth();
    const [pending, setPending] = useState<Action | null>(null);
    const [confirming, setConfirming] = useState<Action | null>(null);

    const available = (variant === "row" ? (["start", "stop", "restart"] as const) : (Object.keys(ACTIONS) as Action[]))
        .filter((action) => hasPermission(user, ACTIONS[action].permission) && ACTIONS[action].appliesTo(container.state))
        // The dashboard cannot stop, restart, or delete itself (also enforced by the backend)
        .filter((action) => !container.dashboard || action === "start");

    async function run(action: Action) {
        setConfirming(null);
        onError(null);
        setPending(action);
        try {
            await ACTIONS[action].run(container.id);
            if (action === "delete") {
                onDeleted?.();
            } else {
                onCompleted();
            }
        } catch (error) {
            onError(error);
        } finally {
            setPending(null);
        }
    }

    if (available.length === 0) {
        return null;
    }

    return (
        <div className="flex flex-wrap gap-2">
            {available.map((action) => {
                const definition = ACTIONS[action];
                const deleteBlocked = action === "delete" && !isStopped(container.state);
                return (
                    <button
                        key={action}
                        type="button"
                        disabled={pending !== null || deleteBlocked}
                        title={deleteBlocked ? "Stop the container before deleting it." : undefined}
                        // Start is not disruptive and runs directly; everything else asks for confirmation.
                        onClick={() => (action === "start" ? void run(action) : setConfirming(action))}
                        className={`inline-flex items-center gap-1.5 rounded-md border font-medium disabled:cursor-not-allowed disabled:opacity-50 ${
                            variant === "row" ? "px-2.5 py-1 text-xs" : "px-3 py-1.5 text-sm"
                        } ${
                            definition.destructive
                                ? "border-red-800 text-red-300 enabled:hover:bg-red-950"
                                : "border-slate-700 text-slate-200 enabled:hover:bg-slate-800"
                        }`}
                    >
                        {pending === action && <Spinner />}
                        {pending === action ? definition.pendingLabel : definition.label}
                    </button>
                );
            })}

            {confirming && (
                <ConfirmModal
                    title={`${ACTIONS[confirming].label} "${container.name}"?`}
                    confirmLabel={ACTIONS[confirming].label}
                    destructive={ACTIONS[confirming].destructive}
                    onConfirm={() => void run(confirming)}
                    onCancel={() => setConfirming(null)}
                >
                    {confirmationText(confirming, mounts)}
                </ConfirmModal>
            )}
        </div>
    );
}

function confirmationText(action: Action, mounts: MountInfo[]): ReactNode {
    switch (action) {
        case "stop":
            return (
                <p>
                    The container gets a grace period to shut down cleanly before it is killed. Connected users
                    (e.g. players) will be disconnected.
                </p>
            );
        case "restart":
            return (
                <p>
                    The container will be stopped and started again. Connected users (e.g. players) will be
                    disconnected.
                </p>
            );
        case "forceStop":
            return (
                <>
                    <p>The container will be killed immediately, without a clean shutdown.</p>
                    <p className="font-medium text-red-300">
                        Unsaved data (e.g. game world progress) may be lost or corrupted. Use "Stop" whenever possible.
                    </p>
                </>
            );
        case "delete":
            return <DeleteConfirmation mounts={mounts} />;
        default:
            return null;
    }
}

/** Anonymous volumes are named by a 64-character hex ID. */
const isAnonymousVolume = (mount: MountInfo) => mount.type === "volume" && /^[0-9a-f]{64}$/.test(mount.name ?? "");

function DeleteConfirmation({ mounts }: { mounts: MountInfo[] }) {
    const persistent = mounts.filter((mount) => mount.type !== "tmpfs");

    return (
        <>
            <p>The container will be permanently deleted. This cannot be undone.</p>
            {persistent.length === 0 ? (
                <p className="font-medium text-red-300">
                    This container has no volumes or bind mounts. All data stored inside the container will be lost.
                </p>
            ) : (
                <>
                    <p>The following data is <strong>kept</strong>:</p>
                    <ul className="flex flex-col gap-1.5">
                        {persistent.map((mount, index) => (
                            <li key={index} className="rounded-md bg-slate-950/60 px-3 py-2">
                                <span className="font-mono text-xs break-all">
                                    {mount.type === "bind" ? mount.source : mount.name}
                                </span>
                                <span className="ml-2 text-xs text-slate-400">
                                    ({mount.type === "bind" ? "bind mount" : "volume"}, mounted at {mount.destination})
                                </span>
                                {isAnonymousVolume(mount) && (
                                    <p className="mt-1 text-xs text-amber-300">
                                        Unnamed volume: it remains, but will be hard to identify after the container is gone.
                                        Note the ID if you need the data later.
                                    </p>
                                )}
                            </li>
                        ))}
                    </ul>
                    <p className="text-slate-400">Data stored inside the container outside these paths will be lost.</p>
                </>
            )}
        </>
    );
}

function Spinner() {
    return (
        <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" className="opacity-25" />
            <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
        </svg>
    );
}
