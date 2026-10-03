import { type FormEvent, useState } from "react";
import { changePassword, MIN_PASSWORD_LENGTH } from "../api/auth";
import { getErrorMessage, getFieldErrors } from "../api/problem";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/Alert";
import { TextField } from "../components/TextField";

export function AccountPage() {
    const { user, refreshUser } = useAuth();
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setSuccess(false);

        const validationErrors: Record<string, string> = {};
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            validationErrors.newPassword = `The password must be at least ${MIN_PASSWORD_LENGTH} characters long.`;
        }
        if (newPassword !== confirmPassword) {
            validationErrors.confirmPassword = "The passwords do not match.";
        }
        setFieldErrors(validationErrors);
        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        setSubmitting(true);
        try {
            await changePassword(currentPassword, newPassword);
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
            setSuccess(true);
            await refreshUser();
        } catch (changeError) {
            setFieldErrors(getFieldErrors(changeError));
            setError(getErrorMessage(changeError));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="flex max-w-lg flex-col gap-8">
            <section>
                <h1 className="text-2xl font-semibold text-white">Account</h1>
                <dl className="mt-4 grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
                    <dt className="text-slate-400">Username</dt>
                    <dd>{user?.username}</dd>
                    <dt className="text-slate-400">Role</dt>
                    <dd>{user?.role?.name ?? "–"}</dd>
                </dl>
            </section>

            <section>
                <h2 className="text-lg font-semibold text-white">Change password</h2>
                <form
                    onSubmit={handleSubmit}
                    className="mt-4 flex flex-col gap-4 rounded-lg border border-slate-800 bg-slate-900 p-6"
                >
                    {error && <Alert variant="error">{error}</Alert>}
                    {success && <Alert variant="success">Your password has been changed.</Alert>}
                    <TextField
                        label="Current password"
                        type="password"
                        value={currentPassword}
                        onChange={(event) => setCurrentPassword(event.target.value)}
                        autoComplete="current-password"
                        error={fieldErrors.currentPassword}
                        required
                    />
                    <TextField
                        label="New password"
                        type="password"
                        value={newPassword}
                        onChange={(event) => setNewPassword(event.target.value)}
                        autoComplete="new-password"
                        error={fieldErrors.newPassword}
                        required
                    />
                    <TextField
                        label="Confirm new password"
                        type="password"
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        autoComplete="new-password"
                        error={fieldErrors.confirmPassword}
                        required
                    />
                    <p className="text-xs text-slate-400">At least {MIN_PASSWORD_LENGTH} characters.</p>
                    <button
                        type="submit"
                        disabled={submitting}
                        className="self-start rounded-md bg-sky-600 px-4 py-2 font-medium text-white hover:bg-sky-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {submitting ? "Saving…" : "Change password"}
                    </button>
                </form>
            </section>
        </div>
    );
}
