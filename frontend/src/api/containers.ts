import type { CategoryInfo } from "./categories";
import { apiClient } from "./client";

/** Docker container state, e.g. "running", "exited", "paused", "restarting", "created", "dead". */
export type ContainerState = string;

/** Why a container is (or is not) considered a game server. */
export type GameServerSource = "MANUAL" | "LABEL" | "IMAGE" | "NONE";

export interface GameServerStatus {
    gameServer: boolean;
    /** Null for generic game servers and non-game-servers. */
    profileId: string | null;
    /** Profile name, "Game server" for generic ones, null if not a game server. */
    profileName: string | null;
    source: GameServerSource;
    /** Manually assigned category (then never a game server), otherwise null. */
    category: CategoryInfo | null;
}

export interface GameServerProfile {
    id: string;
    name: string;
    imageNames: string[];
}

export type ClassificationMode = "AUTOMATIC" | "GAME_SERVER" | "CATEGORY" | "NOT_GAME_SERVER";

export interface ContainerSummary {
    id: string;
    name: string;
    image: string;
    state: ContainerState;
    /** Human-readable status from Docker, e.g. "Up 3 months (healthy)". */
    status: string;
    createdAt: string | null;
    gameServer: GameServerStatus;
    /** The dashboard's own container; it cannot be stopped, restarted or deleted through the dashboard. */
    dashboard: boolean;
}

export interface ContainerOverview {
    statistics: { total: number; running: number; stopped: number; gameServers: number };
    containers: ContainerSummary[];
}

export interface MountInfo {
    type: "volume" | "bind" | "tmpfs";
    name: string | null;
    source: string | null;
    destination: string | null;
    readOnly: boolean;
}

export interface PortMapping {
    containerPort: number;
    protocol: string;
    hostIp: string | null;
    hostPort: string | null;
}

export interface ContainerDetails {
    id: string;
    name: string;
    image: string | null;
    imageId: string | null;
    state: ContainerState | null;
    health: string | null;
    createdAt: string | null;
    startedAt: string | null;
    finishedAt: string | null;
    exitCode: number | null;
    restartCount: number | null;
    mounts: MountInfo[];
    configuration: {
        /** Null if the user may not see environment variables. */
        environment: { name: string; value: string }[] | null;
        environmentHidden: boolean;
        restartPolicy: { name: string; maximumRetryCount: number | null };
        ports: PortMapping[];
        networks: string[];
        labels: Record<string, string>;
    };
    /** Effective status, including a manual classification. */
    gameServer: GameServerStatus;
    /** What automatic detection would result in. */
    detectedGameServer: GameServerStatus;
    /** The dashboard's own container; it cannot be stopped, restarted or deleted through the dashboard. */
    dashboard: boolean;
}

export interface LogLine {
    timestamp: string | null;
    stream: "stdout" | "stderr";
    message: string;
}

export const LOG_TAIL_OPTIONS = [100, 500, 1000, 5000] as const;

export async function getContainerOverview(): Promise<ContainerOverview> {
    const response = await apiClient.get<ContainerOverview>("/containers");
    return response.data;
}

export async function getContainerDetails(id: string): Promise<ContainerDetails> {
    const response = await apiClient.get<ContainerDetails>(`/containers/${encodeURIComponent(id)}`);
    return response.data;
}

export async function getContainerLogs(id: string, tail: number): Promise<LogLine[]> {
    const response = await apiClient.get<{ lines: LogLine[] }>(`/containers/${encodeURIComponent(id)}/logs`, {
        params: { tail },
    });
    return response.data.lines;
}

export async function startContainer(id: string): Promise<void> {
    await apiClient.post(`/containers/${encodeURIComponent(id)}/start`);
}

/** Gives the container a grace period to shut down cleanly before Docker kills it. */
export async function stopContainer(id: string): Promise<void> {
    await apiClient.post(`/containers/${encodeURIComponent(id)}/stop`);
}

export async function restartContainer(id: string): Promise<void> {
    await apiClient.post(`/containers/${encodeURIComponent(id)}/restart`);
}

/** Kills the container immediately, without a clean shutdown. */
export async function forceStopContainer(id: string): Promise<void> {
    await apiClient.post(`/containers/${encodeURIComponent(id)}/force-stop`);
}

/** Deletes a stopped container. Volumes are always kept. */
export async function deleteContainer(id: string): Promise<void> {
    await apiClient.delete(`/containers/${encodeURIComponent(id)}`);
}

/** States in which a container counts as stopped (matches the backend). */
export const STOPPED_STATES: readonly ContainerState[] = ["created", "exited", "dead"];

/** Why a live log stream ended (sent by the backend). */
export type LogStreamEndReason =
    | "container-stopped"
    | "max-duration"
    | "access-revoked"
    | "maintenance"
    | "error"
    | "server-shutdown";

export interface LogStreamHandlers {
    onOpen: () => void;
    onLine: (line: LogLine) => void;
    onEnd: (reason: LogStreamEndReason) => void;
    /** Connection lost or could not be established. */
    onError: () => void;
}

/**
 * Opens a live log stream (Server-Sent Events) that delivers only new log lines.
 * Automatic reconnects are disabled to avoid gaps or duplicate lines; the caller decides when to resume.
 *
 * @returns a function that closes the stream
 */
export function openLogStream(id: string, handlers: LogStreamHandlers): () => void {
    const source = new EventSource(`/api/containers/${encodeURIComponent(id)}/logs/stream`);
    source.addEventListener("ready", () => handlers.onOpen());
    source.addEventListener("line", (event) => handlers.onLine(JSON.parse((event as MessageEvent<string>).data) as LogLine));
    source.addEventListener("end", (event) => {
        source.close();
        handlers.onEnd((JSON.parse((event as MessageEvent<string>).data) as { reason: LogStreamEndReason }).reason);
    });
    source.onerror = () => {
        source.close();
        handlers.onError();
    };
    return () => source.close();
}

/**
 * Sets the manual classification (stored by container name).
 * AUTOMATIC removes it; profileId null with GAME_SERVER means a generic game server; CATEGORY requires categoryId.
 */
export async function classifyContainer(
    id: string,
    mode: ClassificationMode,
    profileId: string | null,
    categoryId: string | null,
): Promise<void> {
    await apiClient.put(`/containers/${encodeURIComponent(id)}/classification`, { mode, profileId, categoryId });
}

export async function listGameServerProfiles(): Promise<GameServerProfile[]> {
    const response = await apiClient.get<GameServerProfile[]>("/game-server-profiles");
    return response.data;
}
