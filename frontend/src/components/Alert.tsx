import type { ReactNode } from "react";

const variants = {
    error: "border-danger-line bg-danger-soft/60 text-danger-fg-strong",
    success: "border-success-line bg-success-soft/60 text-success-fg-strong",
    info: "border-accent-line bg-accent-soft/60 text-accent-fg-strong",
};

interface AlertProps {
    variant: keyof typeof variants;
    children: ReactNode;
}

export function Alert({ variant, children }: AlertProps) {
    return (
        <div role={variant === "error" ? "alert" : "status"} className={`rounded-md border px-4 py-3 text-sm ${variants[variant]}`}>
            {children}
        </div>
    );
}
