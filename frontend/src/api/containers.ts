import { apiClient } from "./client";

/** Docker container state, e.g. "running", "exited", "paused", "restarting", "created", "dead". */
export type ContainerState = string;

export interface ContainerSummary {
    id: string;
    name: string;
    image: string;
    state: ContainerState;
    /** Human-readable status from Docker, e.g. "Up 3 months (healthy)". */
    status: string;
    createdAt: string | null;
}

export interface ContainerOverview {
    statistics: { total: number; running: number; stopped: number };
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
