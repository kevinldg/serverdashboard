import type { ReactNode } from "react";
import { getSystemInfo } from "../../api/system";
import { Alert } from "../../components/Alert";
import { ErrorAlert } from "../../components/ErrorAlert";
import { RefreshButton } from "../../components/RefreshButton";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime, formatDuration } from "../../utils/format";

export function SystemPage() {
    const system = useApiData(getSystemInfo);
    const data = system.data;

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h2 className="text-lg font-semibold text-fg-strong">System information</h2>
                <RefreshButton onRefresh={() => void system.reload()} loading={system.loading} lastUpdated={system.lastUpdated} />
            </div>
            {system.error !== null && <ErrorAlert error={system.error} />}
            {!data && system.loading && <p className="text-fg-muted">Loading…</p>}

            {data && system.lastUpdated && (
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                    <Card title="Application">
                        <Item label="Version">{data.application.version}</Item>
                        <Item label="Commit"><span className="font-mono">{data.application.commit}</span></Item>
                        <Item label="Built">{formatDateTime(data.application.buildTime)}</Item>
                        <Item label="Started">{formatDateTime(data.application.startedAt)}</Item>
                        <Item label="Uptime">
                            {formatDuration(system.lastUpdated.getTime() - new Date(data.application.startedAt).getTime())}
                        </Item>
                        <Item label="Java">{data.application.javaVersion} ({data.application.javaVendor})</Item>
                    </Card>

                    <Card title="Database">
                        <Item label="Database">{data.database.name}</Item>
                        <Item label="Status">
                            {data.database.reachable ? (
                                <span className="text-success-fg">reachable ({data.database.latencyMs} ms)</span>
                            ) : (
                                <span className="text-danger-fg">not reachable</span>
                            )}
                        </Item>
                    </Card>

                    {data.dockerError && (
                        <div className="lg:col-span-2">
                            <Alert variant="error">{data.dockerError}</Alert>
                        </div>
                    )}

                    {data.docker && (
                        <Card title="Docker host">
                            <Item label="Host">{data.docker.hostname}</Item>
                            <Item label="Operating system">{data.docker.operatingSystem}</Item>
                            <Item label="Kernel">{data.docker.kernelVersion}</Item>
                            <Item label="CPUs / memory">
                                {data.docker.cpus} CPUs · {formatBytes(data.docker.memoryBytes)}
                            </Item>
                            <Item label="Docker">{data.docker.serverVersion} (API {data.docker.apiVersion})</Item>
                            <Item label="Storage">{data.docker.storageDriver} · <span className="font-mono">{data.docker.rootDirectory}</span></Item>
                        </Card>
                    )}

                    {data.resources && (
                        <Card title="Resources">
                            <Item label="Containers">
                                {data.resources.containers} ({data.resources.running} running, {data.resources.stopped} stopped)
                            </Item>
                            <Item label="Game servers">{data.resources.gameServers}</Item>
                            <Item label="Images">{data.resources.images}</Item>
                            <Item label="Volumes">{data.resources.volumes}</Item>
                        </Card>
                    )}
                </div>
            )}
        </div>
    );
}

function Card({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className="rounded-lg border border-line bg-surface p-4 sm:p-5">
            <h3 className="mb-3 font-semibold text-fg-strong">{title}</h3>
            <dl className="grid grid-cols-[9rem_minmax(0,1fr)] gap-y-2 text-sm">{children}</dl>
        </section>
    );
}

function Item({ label, children }: { label: string; children: ReactNode }) {
    return (
        <>
            <dt className="text-fg-muted">{label}</dt>
            <dd className="text-fg break-words">{children}</dd>
        </>
    );
}

function formatBytes(bytes: number): string {
    return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`;
}
