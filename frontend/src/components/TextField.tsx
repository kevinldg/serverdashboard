import { type InputHTMLAttributes, useId } from "react";

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "id" | "className"> {
    label: string;
    error?: string;
}

export function TextField({ label, error, ...inputProps }: TextFieldProps) {
    const id = useId();
    const errorId = `${id}-error`;

    return (
        <div className="flex flex-col gap-1.5">
            <label htmlFor={id} className="text-sm font-medium text-fg-secondary">
                {label}
            </label>
            <input
                id={id}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? errorId : undefined}
                className="rounded-md border border-line-strong bg-surface px-3 py-2 text-fg outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 aria-invalid:border-red-500"
                {...inputProps}
            />
            {error && (
                <p id={errorId} className="text-sm text-danger-fg-vivid">
                    {error}
                </p>
            )}
        </div>
    );
}
