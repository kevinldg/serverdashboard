import type { ReactNode } from "react";

const variants = {
    error: "border-red-800 bg-red-950/60 text-red-200",
    success: "border-emerald-800 bg-emerald-950/60 text-emerald-200",
    info: "border-sky-800 bg-sky-950/60 text-sky-200",
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
