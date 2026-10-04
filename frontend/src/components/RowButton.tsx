/** Small action button for table rows (a bit taller on touch screens). A disabled reason disables the button and is shown on hover. */
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
            className={`rounded-md border px-2.5 py-1 text-xs font-medium pointer-coarse:py-1.5 disabled:cursor-not-allowed disabled:opacity-40 ${
                danger ? "border-danger-line text-danger-fg enabled:hover:bg-danger-soft" : "border-line-strong text-fg enabled:hover:bg-raised"
            }`}
        >
            {children}
        </button>
    );
}
