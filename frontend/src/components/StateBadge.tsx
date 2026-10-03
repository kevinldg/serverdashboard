const stateStyles: Record<string, string> = {
    running: "bg-emerald-950 text-emerald-300 ring-emerald-800",
    exited: "bg-red-950 text-red-300 ring-red-800",
    dead: "bg-red-950 text-red-300 ring-red-800",
    paused: "bg-amber-950 text-amber-300 ring-amber-800",
    restarting: "bg-sky-950 text-sky-300 ring-sky-800",
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
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${stateStyles[key] ?? "bg-slate-800 text-slate-300 ring-slate-700"}`}
        >
            <span className={`h-1.5 w-1.5 rounded-full ${dotStyles[key] ?? "bg-slate-400"}`} />
            {key}
        </span>
    );
}
