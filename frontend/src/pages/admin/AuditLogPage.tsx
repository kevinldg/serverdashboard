import { useState } from "react";
import {
    type AuditCategory,
    type AuditEntry,
    type AuditLogQuery,
    type AuditOutcome,
    listAuditActors,
    searchAuditLog,
} from "../../api/auditLog";
import { buttonStyles } from "../../components/buttonStyles";
import { ErrorAlert } from "../../components/ErrorAlert";
import { RefreshButton } from "../../components/RefreshButton";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime, fromDateTimeLocal } from "../../utils/format";

const PAGE_SIZE = 50;

const CATEGORY_LABELS: Record<AuditCategory, string> = {
    AUTHENTICATION: "Login & account",
    CONTAINER: "Containers",
    GAME_SERVER: "Game servers",
    CONFIG_FILE: "Configuration files",
    USER: "Users",
    ROLE: "Roles",
    ANNOUNCEMENT: "Announcements",
    MAINTENANCE: "Maintenance",
    SENSITIVE_READ: "Sensitive reads",
    ACCESS: "Access denied",
};

const OUTCOME_STYLES: Record<AuditOutcome, { label: string; className: string }> = {
    SUCCESS: { label: "success", className: "bg-success-soft text-success-fg ring-success-line" },
    FAILURE: { label: "failed", className: "bg-danger-soft text-danger-fg ring-danger-line" },
    DENIED: { label: "denied", className: "bg-warning-soft text-warning-fg ring-warning-line" },
};

const ISO_TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

interface Filters {
    category: AuditCategory | "";
    actor: string;
    outcome: AuditOutcome | "";
    /** `datetime-local` values (browser time zone) */
    from: string;
    to: string;
}

const NO_FILTERS: Filters = { category: "", actor: "", outcome: "", from: "", to: "" };

export function AuditLogPage() {
    const actors = useApiData(listAuditActors);
    const [filters, setFilters] = useState<Filters>(NO_FILTERS);
    const [page, setPage] = useState(0);

    const query: AuditLogQuery = {
        page,
        size: PAGE_SIZE,
        category: filters.category || null,
        actor: filters.actor || null,
        outcome: filters.outcome || null,
        from: fromDateTimeLocal(filters.from),
        to: fromDateTimeLocal(filters.to),
    };

    function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
        setFilters((current) => ({ ...current, [key]: value }));
        setPage(0);
    }

    const filtered = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);

    return (
        <div className="flex flex-col gap-4">
            <div>
                <h2 className="text-lg font-semibold text-fg-strong">Audit log</h2>
                <p className="text-sm text-fg-muted">
                    Recent activities, newest first. Entries are kept for one year (configurable) and cannot be changed or deleted.
                </p>
            </div>

            <div className="grid grid-cols-1 gap-3 rounded-lg border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-5">
                <FilterSelect
                    label="Category"
                    value={filters.category}
                    onChange={(value) => updateFilter("category", value as AuditCategory | "")}
                    options={Object.entries(CATEGORY_LABELS).map(([value, label]) => ({ value, label }))}
                />
                <FilterSelect
                    label="User"
                    value={filters.actor}
                    onChange={(value) => updateFilter("actor", value)}
                    options={(actors.data ?? []).map((actor) => ({ value: actor, label: actor }))}
                />
                <FilterSelect
                    label="Outcome"
                    value={filters.outcome}
                    onChange={(value) => updateFilter("outcome", value as AuditOutcome | "")}
                    options={Object.entries(OUTCOME_STYLES).map(([value, style]) => ({ value, label: style.label }))}
                />
                <FilterDateTime label="From" value={filters.from} onChange={(value) => updateFilter("from", value)} />
                <FilterDateTime label="Until" value={filters.to} onChange={(value) => updateFilter("to", value)} />
                {filtered && (
                    <div className="sm:col-span-2 lg:col-span-5">
                        <button
                            type="button"
                            onClick={() => {
                                setFilters(NO_FILTERS);
                                setPage(0);
                            }}
                            className="text-sm text-accent-fg-vivid hover:underline"
                        >
                            Reset filters
                        </button>
                    </div>
                )}
            </div>
            {actors.error !== null && <ErrorAlert error={actors.error} />}

            {/* A new query remounts the list, which loads the matching entries */}
            <AuditLogEntries
                key={JSON.stringify(query)}
                query={query}
                onPageChange={setPage}
                onRefresh={() => void actors.reload()}
            />
        </div>
    );
}

interface AuditLogEntriesProps {
    query: AuditLogQuery;
    onPageChange: (page: number) => void;
    /** Called on manual refresh, e.g. to reload the user filter. */
    onRefresh: () => void;
}

function AuditLogEntries({ query, onPageChange, onRefresh }: AuditLogEntriesProps) {
    const result = useApiData(() => searchAuditLog(query));
    const data = result.data;

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-fg-muted">
                    {data && `${data.totalElements} ${data.totalElements === 1 ? "entry" : "entries"}`}
                </p>
                <RefreshButton
                    onRefresh={() => {
                        onRefresh();
                        void result.reload();
                    }}
                    loading={result.loading}
                    lastUpdated={result.lastUpdated}
                />
            </div>

            {result.error !== null && <ErrorAlert error={result.error} />}

            {data && (
                <div className="overflow-x-auto rounded-lg border border-line">
                    <table className="table-stack w-full text-left text-sm">
                        <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                            <tr>
                                <th className="px-4 py-3 font-medium">Activity</th>
                                <th className="px-4 py-3 font-medium">Time</th>
                                <th className="px-4 py-3 font-medium">User</th>
                                <th className="px-4 py-3 font-medium">Category</th>
                                <th className="px-4 py-3 font-medium">Outcome</th>
                                <th className="px-4 py-3 font-medium">IP address</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {data.entries.map((entry) => (
                                <AuditLogRow key={entry.id} entry={entry} />
                            ))}
                            {data.entries.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="px-4 py-6 text-center text-fg-muted">
                                        No entries found.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}
            {!data && result.loading && <p className="text-fg-muted">Loading audit log…</p>}

            {data && data.totalPages > 1 && (
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <button
                        type="button"
                        onClick={() => onPageChange(query.page - 1)}
                        disabled={query.page === 0}
                        className={buttonStyles.secondary}
                    >
                        Newer
                    </button>
                    <span className="text-sm text-fg-muted">
                        Page {query.page + 1} of {data.totalPages}
                    </span>
                    <button
                        type="button"
                        onClick={() => onPageChange(query.page + 1)}
                        disabled={query.page + 1 >= data.totalPages}
                        className={buttonStyles.secondary}
                    >
                        Older
                    </button>
                </div>
            )}
        </div>
    );
}

function AuditLogRow({ entry }: { entry: AuditEntry }) {
    const details = Object.entries(entry.details);
    const outcome = OUTCOME_STYLES[entry.outcome];

    return (
        <tr className="hover:bg-surface/60">
            <td className="px-4 py-3 md:max-w-md">
                <span className="font-medium break-words">{entry.summary}</span>
                {details.length > 0 && (
                    <dl className="mt-1 flex flex-col gap-0.5 text-xs text-fg-muted">
                        {details.map(([key, value]) => (
                            <div key={key} className="flex gap-1.5">
                                <dt className="shrink-0 capitalize">{key}:</dt>
                                <dd className={`break-words whitespace-pre-line ${key === "error" ? "text-danger-fg" : ""}`}>
                                    {ISO_TIMESTAMP.test(value) ? formatDateTime(value) : value}
                                </dd>
                            </div>
                        ))}
                    </dl>
                )}
            </td>
            <td data-label="Time" className="px-4 py-3 whitespace-nowrap text-fg-muted">
                {formatDateTime(entry.timestamp)}
            </td>
            <td data-label="User" className="px-4 py-3 break-all">
                {entry.actor}
            </td>
            <td data-label="Category" className="px-4 py-3 text-fg-muted">
                {CATEGORY_LABELS[entry.category] ?? entry.category}
            </td>
            <td data-label="Outcome" className="px-4 py-3">
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${outcome.className}`}>
                    {outcome.label}
                </span>
            </td>
            <td data-label="IP address" className="px-4 py-3 font-mono text-xs text-fg-muted">
                {entry.ip ?? "–"}
            </td>
        </tr>
    );
}

function FilterSelect({ label, value, onChange, options }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    options: { value: string; label: string }[];
}) {
    return (
        <label className="flex flex-col gap-1.5 text-sm font-medium text-fg-secondary">
            {label}
            <select
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className="rounded-md border border-line-strong bg-surface px-3 py-2 font-normal text-fg"
            >
                <option value="">All</option>
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}

function FilterDateTime({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
    return (
        <label className="flex flex-col gap-1.5 text-sm font-medium text-fg-secondary">
            {label}
            <input
                type="datetime-local"
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className="rounded-md border border-line-strong bg-surface px-3 py-2 font-normal text-fg"
            />
        </label>
    );
}
