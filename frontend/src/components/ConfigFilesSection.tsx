import { Link } from "react-router-dom";
import { getConfigFilesOverview } from "../api/configFiles";
import { useApiData } from "../hooks/useApiData";
import { formatDateTime } from "../utils/format";
import { ErrorAlert } from "./ErrorAlert";

/** Known configuration files and entry points into the file browser, shown on the container details page. */
export function ConfigFilesSection({ containerId }: { containerId: string }) {
    const overview = useApiData(() => getConfigFilesOverview(containerId));

    if (overview.error !== null) {
        return <ErrorAlert error={overview.error} />;
    }
    if (!overview.data) {
        return <p className="text-sm text-fg-muted">Loading…</p>;
    }
    const { knownFiles, roots, running } = overview.data;

    return (
        <div className="flex flex-col gap-4 text-sm">
            {knownFiles.length > 0 && (
                <ul className="flex flex-col divide-y divide-line rounded-md border border-line">
                    {knownFiles.map((file) => (
                        <li key={file.path} className="flex items-center justify-between gap-3 px-4 py-2">
                            <span className="min-w-0">
                                <span className="font-mono">{file.name}</span>
                                <span className="block text-xs wrap-break-word text-fg-subtle sm:ml-3 sm:inline">
                                    {file.path} · {formatDateTime(file.modifiedAt)}
                                </span>
                            </span>
                            {file.editable ? (
                                <Link
                                    to={`/containers/${containerId}/files/edit?path=${encodeURIComponent(file.path)}`}
                                    className="text-accent-fg-vivid hover:underline"
                                >
                                    Open
                                </Link>
                            ) : (
                                <span className="text-xs text-fg-subtle">not editable</span>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {knownFiles.length === 0 && <p className="text-fg-muted">No known configuration files for this game server.</p>}

            <div className="flex flex-wrap items-center gap-3">
                {roots.map((root) =>
                    running ? (
                        <Link
                            key={root}
                            to={`/containers/${containerId}/files?path=${encodeURIComponent(root)}`}
                            className="rounded-md border border-line-strong px-3 py-1.5 text-fg hover:bg-raised"
                        >
                            Browse <span className="font-mono">{root}</span>
                        </Link>
                    ) : (
                        <span key={root} className="rounded-md border border-line px-3 py-1.5 text-fg-subtle">
                            Browse <span className="font-mono">{root}</span>
                        </span>
                    ),
                )}
                {!running && roots.length > 0 && (
                    <span className="text-xs text-fg-muted">Browsing is only possible while the container is running.</span>
                )}
                {roots.length === 0 && <span className="text-xs text-fg-muted">This container has no mounted directories.</span>}
            </div>
        </div>
    );
}
