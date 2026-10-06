import { type ReactNode, useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
    type ContainerDetails,
    getContainerDetails,
    getContainerLogs,
    LOG_TAIL_OPTIONS,
    type LogLine,
    type LogStreamEndReason,
    openLogStream,
} from "../api/containers";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { ConfigFilesSection } from "../components/ConfigFilesSection";
import { ContainerActions } from "../components/ContainerActions";
import { ErrorAlert } from "../components/ErrorAlert";
import { GameServerSection } from "../components/GameServerSection";
import { RefreshButton } from "../components/RefreshButton";
import { StateBadge } from "../components/StateBadge";
import { useApiData } from "../hooks/useApiData";
import { formatDateTime, formatDuration } from "../utils/format";

export function ContainerDetailsPage() {
    const { id = "" } = useParams();
    // Remount when navigating to another container so its data is loaded.
    return <ContainerDetailsView key={id} id={id} />;
}

function ContainerDetailsView({ id }: { id: string }) {
    const { user } = useAuth();
    const { data, error, loading, lastUpdated, reload } = useApiData(() => getContainerDetails(id));
    const [logTail, setLogTail] = useState<number>(LOG_TAIL_OPTIONS[1]);
    const [actionError, setActionError] = useState<unknown>(null);
    const navigate = useNavigate();

    return (
        <div className="flex flex-col gap-6">
            <div>
                <Link to="/" className="text-sm text-accent-fg-vivid hover:underline">
                    ← Dashboard
                </Link>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
                    <div className="flex min-w-0 items-center gap-3">
                        <h1 className="text-2xl font-semibold wrap-break-word text-fg-strong">{data?.name ?? id}</h1>
                        {data && <StateBadge state={data.state} />}
                    </div>
                    <RefreshButton onRefresh={() => void reload()} loading={loading} lastUpdated={lastUpdated} />
                </div>
            </div>

            {data && (
                <ContainerActions
                    container={data}
                    mounts={data.mounts}
                    variant="full"
                    onCompleted={() => void reload()}
                    onDeleted={() => navigate("/", { state: { notice: `Container "${data.name}" was deleted.` } })}
                    onError={setActionError}
                />
            )}

            {actionError !== null && <ErrorAlert error={actionError} />}
            {error !== null && <ErrorAlert error={error} />}
            {!data && loading && <p className="text-fg-muted">Loading container…</p>}

            {data && lastUpdated && (
                <>
                    <GeneralSection container={data} loadedAt={lastUpdated} />
                    <Section title="Classification">
                        <GameServerSection container={data} onChanged={() => void reload()} />
                    </Section>
                    {data.gameServer.gameServer && hasPermission(user, "GAMESERVER_CONFIG_VIEW") && (
                        <Section title="Configuration files">
                            <ConfigFilesSection containerId={data.id} />
                        </Section>
                    )}
                    <StorageSection container={data} />
                    <ConfigurationSection container={data} />
                </>
            )}

            {hasPermission(user, "CONTAINER_LOGS_VIEW") && (
                <Section
                    title="Logs"
                    actions={
                        <label className="flex items-center gap-2 text-sm text-fg-muted">
                            Lines
                            <select
                                value={logTail}
                                onChange={(event) => setLogTail(Number(event.target.value))}
                                className="rounded-md border border-line-strong bg-surface px-2 py-1 text-fg"
                            >
                                {LOG_TAIL_OPTIONS.map((option) => (
                                    <option key={option} value={option}>
                                        {option}
                                    </option>
                                ))}
                            </select>
                        </label>
                    }
                >
                    <ContainerLogs key={logTail} containerId={id} tail={logTail} />
                </Section>
            )}
        </div>
    );
}

function GeneralSection({ container, loadedAt }: { container: ContainerDetails; loadedAt: Date }) {
    const uptime =
        container.state === "running" && container.startedAt
            ? formatDuration(loadedAt.getTime() - new Date(container.startedAt).getTime())
            : null;

    return (
        <Section title="General">
            <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-[10rem_1fr]">
                <Item label="Name">{container.name}</Item>
                <Item label="ID">
                    <span className="font-mono text-xs break-all">{container.id}</span>
                </Item>
                <Item label="Image">
                    <span className="font-mono text-xs">{container.image ?? "–"}</span>
                </Item>
                <Item label="State">
                    {container.state ?? "–"}
                    {container.health && <span className="ml-2 text-fg-muted">({container.health})</span>}
                    {container.state === "exited" && container.exitCode !== null && (
                        <span className="ml-2 text-fg-muted">exit code {container.exitCode}</span>
                    )}
                </Item>
                <Item label="Created">{formatDateTime(container.createdAt)}</Item>
                <Item label="Started">{formatDateTime(container.startedAt)}</Item>
                {uptime && <Item label="Uptime">{uptime}</Item>}
                {container.state !== "running" && container.finishedAt && (
                    <Item label="Stopped">{formatDateTime(container.finishedAt)}</Item>
                )}
                <Item label="Restart count">{container.restartCount ?? 0}</Item>
            </dl>
        </Section>
    );
}

function StorageSection({ container }: { container: ContainerDetails }) {
    return (
        <Section title="Storage">
            {container.mounts.length === 0 ? (
                <p className="text-sm text-fg-muted">No volumes or bind mounts.</p>
            ) : (
                <Table headers={["Type", "Source", "Container path", "Access"]}>
                    {container.mounts.map((mount, index) => (
                        <tr key={index}>
                            <td data-label="Type" className="px-4 py-2 capitalize">{mount.type}</td>
                            <td data-label="Source" className="px-4 py-2 font-mono text-xs break-all">{mount.name ?? mount.source ?? "–"}</td>
                            <td data-label="Container path" className="px-4 py-2 font-mono text-xs break-all">{mount.destination ?? "–"}</td>
                            <td data-label="Access" className="px-4 py-2">{mount.readOnly ? "read-only" : "read/write"}</td>
                        </tr>
                    ))}
                </Table>
            )}
        </Section>
    );
}

function ConfigurationSection({ container }: { container: ContainerDetails }) {
    const { environment, environmentHidden, restartPolicy, ports, networks, labels } = container.configuration;
    const labelEntries = Object.entries(labels);

    return (
        <Section title="Configuration">
            <div className="flex flex-col gap-6">
                <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-[10rem_1fr]">
                    <Item label="Restart policy">
                        {restartPolicy.name}
                        {restartPolicy.name === "on-failure" && restartPolicy.maximumRetryCount
                            ? ` (max. ${restartPolicy.maximumRetryCount} retries)`
                            : ""}
                    </Item>
                    <Item label="Networks">{networks.length > 0 ? networks.join(", ") : "–"}</Item>
                    <Item label="Ports">
                        {ports.length === 0 ? (
                            "–"
                        ) : (
                            <ul className="flex flex-col gap-1 font-mono text-xs">
                                {ports.map((port) => (
                                    <li key={`${port.containerPort}/${port.protocol}/${port.hostPort}`}>
                                        {port.hostPort
                                            ? `${port.hostPort} → ${port.containerPort}/${port.protocol}`
                                            : `${port.containerPort}/${port.protocol} (not published)`}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </Item>
                </dl>

                <div>
                    <h3 className="mb-2 text-sm font-medium text-fg-secondary">Environment variables</h3>
                    {environmentHidden || environment === null ? (
                        <p className="text-sm text-fg-muted">Hidden – you do not have permission to view environment variables.</p>
                    ) : environment.length === 0 ? (
                        <p className="text-sm text-fg-muted">None.</p>
                    ) : (
                        <Table headers={["Name", "Value"]}>
                            {environment.map((variable, index) => (
                                <tr key={index}>
                                    <td data-label="Name" className="px-4 py-2 font-mono text-xs">{variable.name}</td>
                                    <td data-label="Value" className="px-4 py-2 font-mono text-xs break-all">{variable.value}</td>
                                </tr>
                            ))}
                        </Table>
                    )}
                </div>

                {labelEntries.length > 0 && (
                    <details>
                        <summary className="cursor-pointer text-sm font-medium text-fg-secondary">
                            Labels ({labelEntries.length})
                        </summary>
                        <div className="mt-2">
                            <Table headers={["Label", "Value"]}>
                                {labelEntries.map(([key, value]) => (
                                    <tr key={key}>
                                        <td data-label="Label" className="px-4 py-2 font-mono text-xs break-all">{key}</td>
                                        <td data-label="Value" className="px-4 py-2 font-mono text-xs break-all">{value}</td>
                                    </tr>
                                ))}
                            </Table>
                        </div>
                    </details>
                )}
            </div>
        </Section>
    );
}

/** Maximum number of log lines kept in the browser. */
const MAX_LOG_LINES = 5000;

type LiveStatus =
    | { kind: "off" }
    | { kind: "connecting" }
    | { kind: "live" }
    | { kind: "ended"; reason: LogStreamEndReason }
    | { kind: "error" };

const END_MESSAGES: Record<LogStreamEndReason, string> = {
    "container-stopped": "The container stopped. Live logs ended.",
    "max-duration": "Live logs paused after 30 minutes.",
    "access-revoked": "Live logs ended: you no longer have access.",
    maintenance: "Live logs ended: maintenance mode was turned on.",
    "server-shutdown": "The server is restarting. Live logs ended.",
    error: "Live logs ended due to an error.",
};

function ContainerLogs({ containerId, tail }: { containerId: string; tail: number }) {
    const { data, error, loading, lastUpdated, reload } = useApiData(() => getContainerLogs(containerId, tail));
    const [liveEnabled, setLiveEnabled] = useState(false);
    const [streamKey, setStreamKey] = useState(0);
    const [status, setStatus] = useState<LiveStatus>({ kind: "off" });
    const [liveLines, setLiveLines] = useState<LogLine[]>([]);
    const logRef = useRef<HTMLPreElement>(null);
    // Auto-scroll only while the user is at the bottom
    const stickToBottom = useRef(true);

    useEffect(() => {
        if (!liveEnabled) {
            return;
        }
        // Lines are added in batches so chatty containers do not re-render for every line.
        let pending: LogLine[] = [];
        const flushInterval = setInterval(() => {
            if (pending.length > 0) {
                const batch = pending;
                pending = [];
                setLiveLines((previous) => [...previous, ...batch].slice(-MAX_LOG_LINES));
            }
        }, 250);
        const closeStream = openLogStream(containerId, {
            onOpen: () => setStatus({ kind: "live" }),
            onLine: (line) => pending.push(line),
            onEnd: (reason) => setStatus({ kind: "ended", reason }),
            onError: () => setStatus({ kind: "error" }),
        });
        return () => {
            closeStream();
            clearInterval(flushInterval);
        };
    }, [containerId, liveEnabled, streamKey]);

    const lines = [...(data ?? []), ...liveLines].slice(-MAX_LOG_LINES);

    useLayoutEffect(() => {
        const element = logRef.current;
        if (element && stickToBottom.current) {
            element.scrollTop = element.scrollHeight;
        }
    }, [data, liveLines]);

    function toggleLive() {
        if (liveEnabled) {
            setLiveEnabled(false);
            setStatus({ kind: "off" });
        } else {
            stickToBottom.current = true;
            setStatus({ kind: "connecting" });
            setLiveEnabled(true);
        }
    }

    function resume() {
        setStatus({ kind: "connecting" });
        setStreamKey((key) => key + 1);
    }

    function refresh() {
        // The reloaded tail already contains the lines received live so far.
        setLiveLines([]);
        void reload();
    }

    const canResume =
        (status.kind === "ended" && status.reason !== "access-revoked" && status.reason !== "maintenance") || status.kind === "error";

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-3 text-sm">
                    <button
                        type="button"
                        role="switch"
                        aria-checked={liveEnabled}
                        onClick={toggleLive}
                        className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 font-medium ${
                            liveEnabled ? "border-success-line bg-success-soft text-success-fg-strong" : "border-line-strong text-fg hover:bg-raised"
                        }`}
                    >
                        <span className={`h-2 w-2 rounded-full ${status.kind === "live" ? "animate-pulse bg-emerald-400" : "bg-slate-500"}`} />
                        Live
                    </button>
                    {status.kind === "connecting" && <span className="text-fg-muted">Connecting…</span>}
                    {status.kind === "live" && <span className="text-success-fg">Showing new lines as they arrive</span>}
                    {status.kind === "ended" && <span className="text-warning-fg">{END_MESSAGES[status.reason]}</span>}
                    {status.kind === "error" && <span className="text-warning-fg">Connection lost.</span>}
                    {canResume && (
                        <button type="button" onClick={resume} className="text-accent-fg-vivid hover:underline">
                            Resume
                        </button>
                    )}
                </div>
                <RefreshButton onRefresh={refresh} loading={loading} lastUpdated={lastUpdated} />
            </div>
            {error !== null && <ErrorAlert error={error} />}
            {data && (
                <pre
                    ref={logRef}
                    onScroll={(event) => {
                        const element = event.currentTarget;
                        stickToBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 24;
                    }}
                    className="max-h-[60vh] overflow-auto rounded-md sm:max-h-[32rem] border border-line bg-page p-3 font-mono text-xs leading-relaxed"
                >
                    {lines.length === 0 && <span className="text-fg-subtle">No log output.</span>}
                    {lines.map((line, index) => (
                        <div key={index} className={line.stream === "stderr" ? "text-danger-fg" : "text-fg"}>
                            {line.timestamp && (
                                <span className="mr-3 select-none text-fg-subtle">{formatDateTime(line.timestamp)}</span>
                            )}
                            {line.message}
                        </div>
                    ))}
                </pre>
            )}
            {!data && loading && <p className="text-sm text-fg-muted">Loading logs…</p>}
        </div>
    );
}

function Section({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
    return (
        <section className="rounded-lg border border-line bg-surface p-4 sm:p-5">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-lg font-semibold text-fg-strong">{title}</h2>
                {actions}
            </div>
            {children}
        </section>
    );
}

function Item({ label, children }: { label: string; children: ReactNode }) {
    return (
        <>
            <dt className="text-fg-muted">{label}</dt>
            <dd className="text-fg">{children}</dd>
        </>
    );
}

function Table({ headers, children }: { headers: string[]; children: ReactNode }) {
    return (
        <div className="overflow-x-auto rounded-md border border-line">
            <table className="table-stack w-full text-left text-sm">
                <thead className="bg-page/60 text-xs uppercase tracking-wide text-fg-muted">
                    <tr>
                        {headers.map((header) => (
                            <th key={header} className="px-4 py-2 font-medium">
                                {header}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody className="divide-y divide-line">{children}</tbody>
            </table>
        </div>
    );
}
