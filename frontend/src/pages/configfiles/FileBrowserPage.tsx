import { Link, useParams, useSearchParams } from "react-router-dom";
import { getConfigFilesOverview, listDirectory } from "../../api/configFiles";
import { ErrorAlert } from "../../components/ErrorAlert";
import { RefreshButton } from "../../components/RefreshButton";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime } from "../../utils/format";

export function FileBrowserPage() {
    const { id = "" } = useParams();
    const [searchParams] = useSearchParams();
    const path = searchParams.get("path") ?? "";
    // Remount per directory so its listing is loaded.
    return <DirectoryView key={`${id}:${path}`} containerId={id} path={path} />;
}

function DirectoryView({ containerId, path }: { containerId: string; path: string }) {
    const overview = useApiData(() => getConfigFilesOverview(containerId));
    const listing = useApiData(() => listDirectory(containerId, path));
    const root = overview.data?.roots.find((candidate) => path === candidate || path.startsWith(`${candidate}/`));

    return (
        <div className="flex flex-col gap-4">
            <div>
                <Link to={`/containers/${containerId}`} className="text-sm text-sky-400 hover:underline">
                    ← Container
                </Link>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
                    <h1 className="text-2xl font-semibold text-white">Files</h1>
                    <RefreshButton onRefresh={() => void listing.reload()} loading={listing.loading} lastUpdated={listing.lastUpdated} />
                </div>
            </div>

            {root && <Breadcrumbs containerId={containerId} root={root} path={path} />}
            {listing.error !== null && <ErrorAlert error={listing.error} />}

            {listing.data && (
                <div className="overflow-x-auto rounded-lg border border-slate-800">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-slate-900 text-xs uppercase tracking-wide text-slate-400">
                            <tr>
                                <th className="px-4 py-2 font-medium">Name</th>
                                <th className="px-4 py-2 font-medium">Size</th>
                                <th className="px-4 py-2 font-medium">Modified</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800">
                            {listing.data.entries.map((entry) => (
                                <tr key={entry.path} className="hover:bg-slate-900/60">
                                    <td className="px-4 py-2 font-mono">
                                        {entry.type === "DIRECTORY" ? (
                                            <Link to={`/containers/${containerId}/files?path=${encodeURIComponent(entry.path)}`}
                                                  className="text-sky-400 hover:underline">
                                                {entry.name}/
                                            </Link>
                                        ) : entry.editable ? (
                                            <Link to={`/containers/${containerId}/files/edit?path=${encodeURIComponent(entry.path)}`}
                                                  className="text-sky-300 hover:underline">
                                                {entry.name}
                                            </Link>
                                        ) : (
                                            <span className="text-slate-400" title={entry.type === "SYMLINK" ? "Symbolic link (not followed)" : "Cannot be edited"}>
                                                {entry.name}
                                                {entry.type === "SYMLINK" && " →"}
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-4 py-2 text-slate-400">{entry.type === "DIRECTORY" ? "–" : formatSize(entry.size)}</td>
                                    <td className="px-4 py-2 text-slate-400">{formatDateTime(entry.modifiedAt)}</td>
                                </tr>
                            ))}
                            {listing.data.entries.length === 0 && (
                                <tr>
                                    <td colSpan={3} className="px-4 py-6 text-center text-slate-400">This directory is empty.</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}
            {!listing.data && listing.loading && <p className="text-slate-400">Loading…</p>}
            <p className="text-xs text-slate-500">
                Text files up to 1 MB with the extensions .properties .json .yml .yaml .toml .txt .cfg .conf .ini .xml .env .sh
                can be opened. Symbolic links are not followed.
            </p>
        </div>
    );
}

/** Clickable path segments, starting at the browser root (nothing above it is reachable). */
function Breadcrumbs({ containerId, root, path }: { containerId: string; root: string; path: string }) {
    const relative = path.slice(root.length).split("/").filter(Boolean);
    const crumbs = [{ label: root, path: root }, ...relative.map((segment, index) => ({
        label: segment,
        path: `${root}/${relative.slice(0, index + 1).join("/")}`,
    }))];
    return (
        <nav className="flex flex-wrap items-center gap-1 font-mono text-sm">
            {crumbs.map((crumb, index) => (
                <span key={crumb.path} className="flex items-center gap-1">
                    {index > 0 && <span className="text-slate-600">/</span>}
                    {index === crumbs.length - 1 ? (
                        <span className="text-slate-200">{crumb.label}</span>
                    ) : (
                        <Link to={`/containers/${containerId}/files?path=${encodeURIComponent(crumb.path)}`} className="text-sky-400 hover:underline">
                            {crumb.label}
                        </Link>
                    )}
                </span>
            ))}
        </nav>
    );
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`;
}
