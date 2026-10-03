import { formatTime } from "../utils/format";

interface RefreshButtonProps {
    onRefresh: () => void;
    loading: boolean;
    lastUpdated: Date | null;
}

export function RefreshButton({ onRefresh, loading, lastUpdated }: RefreshButtonProps) {
    return (
        <div className="flex items-center gap-3">
            {lastUpdated && (
                <span className="text-xs text-slate-400">Last updated: {formatTime(lastUpdated)}</span>
            )}
            <button
                type="button"
                onClick={onRefresh}
                disabled={loading}
                className="inline-flex items-center gap-2 rounded-md border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm font-medium text-slate-200 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
                <svg
                    className={`h-4 w-4 ${loading ? "animate-spin" : ""}`}
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    aria-hidden="true"
                >
                    <path d="M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                {loading ? "Refreshing…" : "Refresh"}
            </button>
        </div>
    );
}
