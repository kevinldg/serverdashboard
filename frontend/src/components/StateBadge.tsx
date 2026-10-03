const stateStyles: Record<string, string> = {
    running: "bg-success-soft text-success-fg ring-success-line",
    exited: "bg-danger-soft text-danger-fg ring-danger-line",
    dead: "bg-danger-soft text-danger-fg ring-danger-line",
    paused: "bg-warning-soft text-warning-fg ring-warning-line",
    restarting: "bg-accent-soft text-accent-fg ring-accent-line",
};

const dotStyles: Record<string, string> = {
    running: "bg-emerald-400",
    exited: "bg-red-400",
    dead: "bg-red-400",
    paused: "bg-amber-400",
    restarting: "bg-sky-400 animate-pulse",
};

export function StateBadge({ state }: { state: string | null }) {
    const key = state ?? "unknown";
    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${stateStyles[key] ?? "bg-raised text-fg-secondary ring-line-strong"}`}
        >
            <span className={`h-1.5 w-1.5 rounded-full ${dotStyles[key] ?? "bg-slate-400"}`} />
            {key}
        </span>
    );
}
