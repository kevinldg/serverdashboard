/** Small action button for table rows. A disabled reason disables the button and is shown on hover. */
export function RowButton({ children, onClick, danger = false, disabledReason }: {
    children: string;
    onClick: () => void;
    danger?: boolean;
    disabledReason?: string;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabledReason !== undefined}
            title={disabledReason}
            className={`rounded-md border px-2.5 py-1 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-40 ${
                danger ? "border-red-800 text-red-300 enabled:hover:bg-red-950" : "border-slate-700 text-slate-200 enabled:hover:bg-slate-800"
            }`}
        >
            {children}
        </button>
    );
}
