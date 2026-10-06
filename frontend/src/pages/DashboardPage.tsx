import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { listVisibleAnnouncements } from "../api/announcements";
import type { CategoryInfo } from "../api/categories";
import { type ContainerSummary, getContainerOverview } from "../api/containers";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/Alert";
import { buttonStyles } from "../components/buttonStyles";
import { AnnouncementList } from "../components/AnnouncementList";
import { CategoryBadge } from "../components/CategoryBadge";
import { ContainerActions } from "../components/ContainerActions";
import { ErrorAlert } from "../components/ErrorAlert";
import { GameServerBadge } from "../components/GameServerBadge";
import { RefreshButton } from "../components/RefreshButton";
import { StateBadge } from "../components/StateBadge";
import { useApiData } from "../hooks/useApiData";

/** "all", "game-servers", "unclassified", or "category:<id>" */
type ClassificationFilter = string;

const CATEGORY_FILTER_PREFIX = "category:";

function matchesFilter(container: ContainerSummary, filter: ClassificationFilter): boolean {
    const { gameServer } = container;
    switch (filter) {
        case "all":
            return true;
        case "game-servers":
            return gameServer.gameServer;
        case "unclassified":
            return !gameServer.gameServer && gameServer.category === null;
        default:
            return gameServer.category?.id === filter.slice(CATEGORY_FILTER_PREFIX.length);
    }
}

/** Categories assigned to at least one container, sorted by name. */
function categoriesInUse(containers: ContainerSummary[]): CategoryInfo[] {
    const categories = new Map<string, CategoryInfo>();
    containers.forEach((container) => {
        if (container.gameServer.category) {
            categories.set(container.gameServer.category.id, container.gameServer.category);
        }
    });
    return [...categories.values()].sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }));
}

export function DashboardPage() {
    const { user } = useAuth();
    const { data, error, loading, lastUpdated, reload } = useApiData(getContainerOverview);
    const announcements = useApiData(listVisibleAnnouncements);
    const location = useLocation();
    const [actionError, setActionError] = useState<unknown>(null);
    const [selectedFilter, setSelectedFilter] = useState<ClassificationFilter>("all");
    const canViewDetails = hasPermission(user, "CONTAINER_VIEW");
    const canRunActions = (["CONTAINER_START", "CONTAINER_STOP", "CONTAINER_RESTART"] as const)
        .some((permission) => hasPermission(user, permission));
    // Set by the details page after deleting a container
    const notice = (location.state as { notice?: string } | null)?.notice;
    const categories = categoriesInUse(data?.containers ?? []);
    const filterOptions = [
        { value: "all", label: "All containers" },
        { value: "game-servers", label: "Game servers" },
        ...categories.map((category) => ({ value: CATEGORY_FILTER_PREFIX + category.id, label: category.name })),
        { value: "unclassified", label: "Unclassified" },
    ];
    // A category that is no longer assigned (e.g. after a refresh) shows all containers again
    const filter = filterOptions.some((option) => option.value === selectedFilter) ? selectedFilter : "all";
    const visibleContainers = data?.containers.filter((container) => matchesFilter(container, filter)) ?? [];

    return (
        <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h1 className="text-2xl font-semibold text-fg-strong">Dashboard</h1>
                <div className="flex flex-wrap items-center gap-3">
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
                        <StatCard label="Running" value={data.statistics.running} accent="text-success-fg-vivid" />
                        <StatCard label="Stopped" value={data.statistics.stopped} accent="text-danger-fg-vivid" />
                        <StatCard label="Game servers" value={data.statistics.gameServers} accent="text-game-fg-vivid" />
                    </div>

                    <label className="flex items-center gap-2 self-start text-sm text-fg-secondary">
                        Show
                        <select
                            value={filter}
                            onChange={(event) => setSelectedFilter(event.target.value)}
                            className="rounded-md border border-line-strong bg-surface px-3 py-1.5 text-fg"
                        >
                            {filterOptions.map((option) => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    </label>

                    <div className="overflow-x-auto rounded-lg border border-line">
                        <table className="table-stack w-full text-left text-sm">
                            <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                                <tr>
                                    <th className="px-4 py-3 font-medium">Name</th>
                                    <th className="px-4 py-3 font-medium">Image</th>
                                    <th className="px-4 py-3 font-medium">State</th>
                                    <th className="px-4 py-3 font-medium">Status</th>
                                    {canRunActions && <th className="px-4 py-3 font-medium">Actions</th>}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-line">
                                {visibleContainers.map((container) => (
                                    <tr key={container.id} className="hover:bg-surface/60">
                                        <td className="px-4 py-3 font-medium">
                                            {canViewDetails ? (
                                                <Link
                                                    to={`/containers/${container.id}`}
                                                    className="text-accent-fg-vivid hover:text-accent-fg hover:underline"
                                                >
                                                    {container.name}
                                                </Link>
                                            ) : (
                                                container.name
                                            )}
                                            <span className="ml-2">
                                                <GameServerBadge status={container.gameServer} />
                                                {container.gameServer.category && (
                                                    <CategoryBadge category={container.gameServer.category} />
                                                )}
                                            </span>
                                            {container.dashboard && (
                                                <span className="ml-2 rounded-full bg-accent-soft px-2 py-0.5 text-xs font-medium text-accent-fg ring-1 ring-inset ring-accent-line">
                                                    this dashboard
                                                </span>
                                            )}
                                        </td>
                                        <td data-label="Image" className="px-4 py-3 font-mono text-xs text-fg-secondary">{container.image}</td>
                                        <td data-label="State" className="px-4 py-3">
                                            <StateBadge state={container.state} />
                                        </td>
                                        <td data-label="Status" className="px-4 py-3 text-fg-muted">{container.status}</td>
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
                                        <td colSpan={canRunActions ? 5 : 4} className="px-4 py-6 text-center text-fg-muted">
                                            {filter === "all" ? "No containers found." : "No matching containers found."}
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </>
            )}

            {!data && loading && <p className="text-fg-muted">Loading containers…</p>}
        </div>
    );
}

function StatCard({ label, value, accent = "text-fg-strong" }: { label: string; value: number; accent?: string }) {
    return (
        <div className="rounded-lg border border-line bg-surface px-5 py-4">
            <p className="text-sm text-fg-muted">{label}</p>
            <p className={`mt-1 text-3xl font-semibold ${accent}`}>{value}</p>
        </div>
    );
}
