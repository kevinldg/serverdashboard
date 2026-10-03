import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { listVisibleAnnouncements } from "../api/announcements";
import { getContainerOverview } from "../api/containers";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/Alert";
import { buttonStyles } from "../components/buttonStyles";
import { AnnouncementList } from "../components/AnnouncementList";
import { ContainerActions } from "../components/ContainerActions";
import { ErrorAlert } from "../components/ErrorAlert";
import { GameServerBadge } from "../components/GameServerBadge";
import { RefreshButton } from "../components/RefreshButton";
import { StateBadge } from "../components/StateBadge";
import { useApiData } from "../hooks/useApiData";

export function DashboardPage() {
    const { user } = useAuth();
    const { data, error, loading, lastUpdated, reload } = useApiData(getContainerOverview);
    const announcements = useApiData(listVisibleAnnouncements);
    const location = useLocation();
    const [actionError, setActionError] = useState<unknown>(null);
    const [gameServersOnly, setGameServersOnly] = useState(false);
    const canViewDetails = hasPermission(user, "CONTAINER_VIEW");
    const canRunActions = (["CONTAINER_START", "CONTAINER_STOP", "CONTAINER_RESTART"] as const)
        .some((permission) => hasPermission(user, permission));
    // Set by the details page after deleting a container
    const notice = (location.state as { notice?: string } | null)?.notice;
    const visibleContainers = data?.containers.filter((container) => !gameServersOnly || container.gameServer.gameServer) ?? [];

    return (
        <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h1 className="text-2xl font-semibold text-white">Dashboard</h1>
                <div className="flex items-center gap-3">
                    <RefreshButton
                        onRefresh={() => {
                            void reload();
                            void announcements.reload();
                        }}
                        loading={loading || announcements.loading}
                        lastUpdated={lastUpdated}
                    />
                    {hasPermission(user, "CONTAINER_CREATE") && (
                        <Link to="/containers/new" className={buttonStyles.primary}>
                            Create container
                        </Link>
                    )}
                </div>
            </div>

            {announcements.data && <AnnouncementList announcements={announcements.data} />}
            {notice && <Alert variant="success">{notice}</Alert>}
            {error !== null && <ErrorAlert error={error} />}
            {actionError !== null && <ErrorAlert error={actionError} />}

            {data && (
                <>
                    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                        <StatCard label="Total containers" value={data.statistics.total} />
                        <StatCard label="Running" value={data.statistics.running} accent="text-emerald-400" />
                        <StatCard label="Stopped" value={data.statistics.stopped} accent="text-red-400" />
                        <StatCard label="Game servers" value={data.statistics.gameServers} accent="text-violet-400" />
                    </div>

                    <label className="flex items-center gap-2 self-start text-sm text-slate-300">
                        <input type="checkbox" checked={gameServersOnly} onChange={(event) => setGameServersOnly(event.target.checked)} />
                        Game servers only
                    </label>

                    <div className="overflow-x-auto rounded-lg border border-slate-800">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-slate-900 text-xs uppercase tracking-wide text-slate-400">
                                <tr>
                                    <th className="px-4 py-3 font-medium">Name</th>
                                    <th className="px-4 py-3 font-medium">Image</th>
                                    <th className="px-4 py-3 font-medium">State</th>
                                    <th className="px-4 py-3 font-medium">Status</th>
                                    {canRunActions && <th className="px-4 py-3 font-medium">Actions</th>}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-800">
                                {visibleContainers.map((container) => (
                                    <tr key={container.id} className="hover:bg-slate-900/60">
                                        <td className="px-4 py-3 font-medium">
                                            {canViewDetails ? (
                                                <Link
                                                    to={`/containers/${container.id}`}
                                                    className="text-sky-400 hover:text-sky-300 hover:underline"
                                                >
                                                    {container.name}
                                                </Link>
                                            ) : (
                                                container.name
                                            )}
                                            <span className="ml-2">
                                                <GameServerBadge status={container.gameServer} />
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 font-mono text-xs text-slate-300">{container.image}</td>
                                        <td className="px-4 py-3">
                                            <StateBadge state={container.state} />
                                        </td>
                                        <td className="px-4 py-3 text-slate-400">{container.status}</td>
                                        {canRunActions && (
                                            <td className="px-4 py-3">
                                                <ContainerActions
                                                    container={container}
                                                    variant="row"
                                                    onCompleted={() => void reload()}
                                                    onError={setActionError}
                                                />
                                            </td>
                                        )}
                                    </tr>
                                ))}
                                {visibleContainers.length === 0 && (
                                    <tr>
                                        <td colSpan={canRunActions ? 5 : 4} className="px-4 py-6 text-center text-slate-400">
                                            {gameServersOnly ? "No game servers found." : "No containers found."}
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </>
            )}

            {!data && loading && <p className="text-slate-400">Loading containers…</p>}
        </div>
    );
}

function StatCard({ label, value, accent = "text-white" }: { label: string; value: number; accent?: string }) {
    return (
        <div className="rounded-lg border border-slate-800 bg-slate-900 px-5 py-4">
            <p className="text-sm text-slate-400">{label}</p>
            <p className={`mt-1 text-3xl font-semibold ${accent}`}>{value}</p>
        </div>
    );
}
