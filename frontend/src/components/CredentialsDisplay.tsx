import { useEffect, useState } from "react";

/**
 * Shows newly generated credentials: password hidden by default, with reveal and copy-to-clipboard.
 * The Clipboard API only works in secure contexts (HTTPS or localhost); otherwise the password can be
 * revealed and copied manually.
 */
export function CredentialsDisplay({ username, password }: { username: string; password: string }) {
    const [revealed, setRevealed] = useState(false);
    const [copyState, setCopyState] = useState<"idle" | "copied" | "unavailable">("idle");

    useEffect(() => {
        if (copyState !== "copied") return;
        const timeout = setTimeout(() => setCopyState("idle"), 2000);
        return () => clearTimeout(timeout);
    }, [copyState]);

    async function copyCredentials() {
        try {
            if (!window.isSecureContext || !navigator.clipboard) {
                throw new Error("Clipboard not available");
            }
            await navigator.clipboard.writeText(`Username: ${username}\nPassword: ${password}`);
            setCopyState("copied");
        } catch {
            setCopyState("unavailable");
            setRevealed(true);
        }
    }

    return (
        <div className="flex flex-col gap-3">
            <dl className="grid grid-cols-[6rem_1fr] items-center gap-y-2 rounded-md bg-page/60 px-4 py-3 text-sm">
                <dt className="text-fg-muted">Username</dt>
                <dd className="font-mono">{username}</dd>
                <dt className="text-fg-muted">Password</dt>
                <dd className="flex items-center gap-2">
                    {revealed ? (
                        <input
                            readOnly
                            value={password}
                            aria-label="Password"
                            onFocus={(event) => event.target.select()}
                            className="w-full rounded border border-line-strong bg-surface px-2 py-1 font-mono text-sm"
                        />
                    ) : (
                        <span className="font-mono tracking-widest text-fg-secondary" aria-label="Password hidden">
                            {"•".repeat(password.length)}
                        </span>
                    )}
                    <button
                        type="button"
                        onClick={() => setRevealed(!revealed)}
                        className="shrink-0 text-xs text-accent-fg-vivid hover:underline"
                    >
                        {revealed ? "Hide" : "Show"}
                    </button>
                </dd>
            </dl>

            <div className="flex items-center gap-3">
                <button
                    type="button"
                    onClick={() => void copyCredentials()}
                    className="rounded-md border border-line-strong px-3 py-1.5 text-sm font-medium text-fg hover:bg-raised"
                >
                    Copy credentials
                </button>
                {copyState === "copied" && <span className="text-sm text-success-fg-vivid">Copied to clipboard.</span>}
            </div>
            {copyState === "unavailable" && (
                <p className="text-xs text-warning-fg">
                    Copying is not available here (it requires HTTPS). The password is now shown above; select it to
                    copy it manually.
                </p>
            )}
            <p className="text-xs text-fg-muted">
                This password is shown only once. Pass it on to the user securely.
            </p>
        </div>
    );
}
