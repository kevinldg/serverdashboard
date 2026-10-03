import { type FormEvent, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { getErrorMessage } from "../api/problem";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/Alert";
import { TextField } from "../components/TextField";
import { useMaintenance } from "../maintenance/useMaintenance";

export function LoginPage() {
    const { user, login } = useAuth();
    const { status: maintenance } = useMaintenance();
    const location = useLocation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    if (user) {
        const from = (location.state as { from?: string } | null)?.from ?? "/";
        return <Navigate to={from} replace />;
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await login(username, password);
        } catch (loginError) {
            setError(getErrorMessage(loginError));
            setPassword("");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="flex min-h-screen items-center justify-center px-4">
            <div className="w-full max-w-sm">
                <h1 className="mb-6 text-center text-2xl font-semibold text-white">ServerDashboard</h1>
                <form
                    onSubmit={handleSubmit}
                    className="flex flex-col gap-4 rounded-lg border border-slate-800 bg-slate-900 p-6"
                >
                    {maintenance?.enabled && (
                        <Alert variant="info">Maintenance mode is active. Only administrators can log in.</Alert>
                    )}
                    {error && <Alert variant="error">{error}</Alert>}
                    <TextField
                        label="Username"
                        value={username}
                        onChange={(event) => setUsername(event.target.value)}
                        autoComplete="username"
                        autoFocus
                        required
                    />
                    <TextField
                        label="Password"
                        type="password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        autoComplete="current-password"
                        required
                    />
                    <button
                        type="submit"
                        disabled={submitting}
                        className="mt-2 rounded-md bg-sky-600 px-4 py-2 font-medium text-white hover:bg-sky-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {submitting ? "Logging in…" : "Log in"}
                    </button>
                </form>
            </div>
        </div>
    );
}
