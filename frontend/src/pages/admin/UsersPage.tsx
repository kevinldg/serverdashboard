import { type FormEvent, useState } from "react";
import { MIN_PASSWORD_LENGTH } from "../../api/auth";
import { getErrorMessage, getFieldErrors } from "../../api/problem";
import {
    createUser,
    deleteUser,
    listRoleOptions,
    listUsers,
    type ManagedUser,
    resetPassword,
    type RoleOption,
    updateUser,
    USERNAME_PATTERN,
} from "../../api/users";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ConfirmModal } from "../../components/ConfirmModal";
import { CredentialsDisplay } from "../../components/CredentialsDisplay";
import { ErrorAlert } from "../../components/ErrorAlert";
import { Modal } from "../../components/Modal";
import { RefreshButton } from "../../components/RefreshButton";
import { RowButton } from "../../components/RowButton";
import { TextField } from "../../components/TextField";
import { useApiData } from "../../hooks/useApiData";
import { formatDateTime } from "../../utils/format";

type Dialog =
    | { type: "create" }
    | { type: "edit"; user: ManagedUser }
    | { type: "reset"; user: ManagedUser }
    | { type: "delete"; user: ManagedUser }
    | { type: "credentials"; title: string; username: string; password: string };

export function UsersPage() {
    const { user: currentUser } = useAuth();
    const users = useApiData(listUsers);
    const roles = useApiData(listRoleOptions);
    const [dialog, setDialog] = useState<Dialog | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [actionError, setActionError] = useState<unknown>(null);

    const isSelf = (user: ManagedUser) => user.id === currentUser?.id;
    // Only administrators may manage administrator accounts (also enforced by the backend).
    const mayManage = (user: ManagedUser) => !user.admin || currentUser?.admin === true;

    async function runAction(action: () => Promise<void>) {
        setDialog(null);
        setNotice(null);
        setActionError(null);
        try {
            await action();
        } catch (error) {
            setActionError(error);
        }
        await users.reload();
    }

    function handleSaved(message: string, credentials?: { username: string; password: string }) {
        setNotice(message);
        setActionError(null);
        setDialog(credentials ? { type: "credentials", title: "User created", ...credentials } : null);
        void users.reload();
    }

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h2 className="text-lg font-semibold text-fg-strong">Users</h2>
                <div className="flex flex-wrap items-center gap-3">
                    <RefreshButton onRefresh={() => void users.reload()} loading={users.loading} lastUpdated={users.lastUpdated} />
                    <button type="button" onClick={() => setDialog({ type: "create" })} className={buttonStyles.primary}>
                        Create user
                    </button>
                </div>
            </div>

            {notice && <Alert variant="success">{notice}</Alert>}
            {actionError !== null && <ErrorAlert error={actionError} />}
            {users.error !== null && <ErrorAlert error={users.error} />}

            {users.data && (
                <div className="overflow-x-auto rounded-lg border border-line">
                    <table className="table-stack w-full text-left text-sm">
                        <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                            <tr>
                                <th className="px-4 py-3 font-medium">Username</th>
                                <th className="px-4 py-3 font-medium">Role</th>
                                <th className="px-4 py-3 font-medium">Status</th>
                                <th className="px-4 py-3 font-medium">Last login</th>
                                <th className="px-4 py-3 font-medium">Created</th>
                                <th className="px-4 py-3 font-medium">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {users.data.map((user) => (
                                <tr key={user.id} className="hover:bg-surface/60">
                                    <td className="px-4 py-3 font-medium">
                                        {user.username}
                                        {isSelf(user) && <span className="ml-2 text-xs text-fg-muted">(you)</span>}
                                    </td>
                                    <td data-label="Role" className="px-4 py-3">
                                        {user.role?.name ?? <span className="text-danger-fg">missing role</span>}
                                    </td>
                                    <td data-label="Status" className="px-4 py-3">
                                        <span
                                            className={`rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
                                                user.active
                                                    ? "bg-success-soft text-success-fg ring-success-line"
                                                    : "bg-raised text-fg-secondary ring-line-strong"
                                            }`}
                                        >
                                            {user.active ? "active" : "deactivated"}
                                        </span>
                                        {user.passwordChangeRecommended && (
                                            <span className="ml-2 text-xs text-warning-fg" title="Still using an initial or reset password">
                                                initial password
                                            </span>
                                        )}
                                    </td>
                                    <td data-label="Last login" className="px-4 py-3 text-fg-muted">{formatDateTime(user.lastLoginAt)}</td>
                                    <td data-label="Created" className="px-4 py-3 text-fg-muted">{formatDateTime(user.createdAt)}</td>
                                    <td className="px-4 py-3">
                                        {mayManage(user) ? (
                                            <div className="flex flex-wrap gap-2">
                                                <RowButton onClick={() => setDialog({ type: "edit", user })}>Edit</RowButton>
                                                <RowButton
                                                    onClick={() => setDialog({ type: "reset", user })}
                                                    disabledReason={isSelf(user) ? "Use the account page to change your own password." : undefined}
                                                >
                                                    Reset password
                                                </RowButton>
                                                <RowButton
                                                    danger
                                                    onClick={() => setDialog({ type: "delete", user })}
                                                    disabledReason={isSelf(user) ? "You cannot delete your own account." : undefined}
                                                >
                                                    Delete
                                                </RowButton>
                                            </div>
                                        ) : (
                                            <span className="text-xs text-fg-subtle">Only administrators can manage this account.</span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            {!users.data && users.loading && <p className="text-fg-muted">Loading users…</p>}

            {(dialog?.type === "create" || dialog?.type === "edit") && (
                <UserFormModal
                    user={dialog.type === "edit" ? dialog.user : null}
                    isSelf={dialog.type === "edit" && isSelf(dialog.user)}
                    roles={roles.data ?? []}
                    mayAssignAdmin={currentUser?.admin === true}
                    onSaved={handleSaved}
                    onClose={() => setDialog(null)}
                />
            )}

            {dialog?.type === "reset" && (
                <ConfirmModal
                    title={`Reset the password of "${dialog.user.username}"?`}
                    confirmLabel="Reset password"
                    onCancel={() => setDialog(null)}
                    onConfirm={() => {
                        const target = dialog.user;
                        void runAction(async () => {
                            const result = await resetPassword(target.id);
                            setNotice(`The password of "${result.username}" was reset.`);
                            setDialog({
                                type: "credentials",
                                title: "New password",
                                username: result.username,
                                password: result.generatedPassword,
                            });
                        });
                    }}
                >
                    <p>A new random password will be generated. The old password stops working immediately.</p>
                    <p>The user is logged out on all devices.</p>
                </ConfirmModal>
            )}

            {dialog?.type === "delete" && (
                <ConfirmModal
                    title={`Delete user "${dialog.user.username}"?`}
                    confirmLabel="Delete user"
                    destructive
                    onCancel={() => setDialog(null)}
                    onConfirm={() => {
                        const target = dialog.user;
                        void runAction(async () => {
                            await deleteUser(target.id);
                            setNotice(`User "${target.username}" was deleted.`);
                        });
                    }}
                >
                    <p>The user will be permanently deleted and logged out immediately. This cannot be undone.</p>
                    <p className="text-fg-muted">To block access temporarily instead, deactivate the user.</p>
                </ConfirmModal>
            )}

            {dialog?.type === "credentials" && (
                <Modal title={dialog.title} onClose={() => setDialog(null)}>
                    <CredentialsDisplay username={dialog.username} password={dialog.password} />
                    <div className="mt-6 flex justify-end">
                        <button type="button" onClick={() => setDialog(null)} className={buttonStyles.primary}>
                            Done
                        </button>
                    </div>
                </Modal>
            )}
        </div>
    );
}

interface UserFormModalProps {
    /** Null to create a new user. */
    user: ManagedUser | null;
    isSelf: boolean;
    roles: RoleOption[];
    mayAssignAdmin: boolean;
    onSaved: (message: string, credentials?: { username: string; password: string }) => void;
    onClose: () => void;
}

function UserFormModal({ user, isSelf, roles, mayAssignAdmin, onSaved, onClose }: UserFormModalProps) {
    const isNew = user === null;
    const assignableRoles = roles.filter((role) => mayAssignAdmin || !role.admin);
    const defaultRole = assignableRoles.find((role) => !role.admin && role.name === "User") ?? assignableRoles[0];

    const [username, setUsername] = useState(user?.username ?? "");
    const [roleId, setRoleId] = useState(user?.role?.id ?? defaultRole?.id ?? "");
    const [active, setActive] = useState(user?.active ?? true);
    const [generatePassword, setGeneratePassword] = useState(true);
    const [password, setPassword] = useState("");
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);

        const trimmedUsername = username.trim();
        const validationErrors: Record<string, string> = {};
        if (!USERNAME_PATTERN.test(trimmedUsername)) {
            validationErrors.username = "3–32 characters: letters, digits, dots, underscores and hyphens.";
        }
        if (!roleId) {
            validationErrors.roleId = "Please select a role.";
        }
        if (isNew && !generatePassword && password.length < MIN_PASSWORD_LENGTH) {
            validationErrors.password = `The password must be at least ${MIN_PASSWORD_LENGTH} characters long.`;
        }
        setFieldErrors(validationErrors);
        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        setSaving(true);
        try {
            if (isNew) {
                const result = await createUser(trimmedUsername, roleId, generatePassword ? null : password);
                onSaved(
                    `User "${result.user.username}" was created.`,
                    result.generatedPassword ? { username: result.user.username, password: result.generatedPassword } : undefined,
                );
            } else {
                const updated = await updateUser(user.id, trimmedUsername, roleId, active);
                onSaved(`User "${updated.username}" was updated.`);
            }
        } catch (saveError) {
            setFieldErrors(getFieldErrors(saveError));
            setError(getErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal title={isNew ? "Create user" : `Edit user "${user.username}"`} onClose={onClose} size="lg">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                {error && <Alert variant="error">{error}</Alert>}

                <TextField
                    label="Username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    autoComplete="off"
                    error={fieldErrors.username}
                    required
                />

                <div className="flex flex-col gap-1.5">
                    <label htmlFor="user-role" className="text-sm font-medium text-fg-secondary">
                        Role
                    </label>
                    <select
                        id="user-role"
                        value={roleId}
                        onChange={(event) => setRoleId(event.target.value)}
                        disabled={isSelf}
                        className="rounded-md border border-line-strong bg-surface px-3 py-2 text-fg disabled:opacity-60"
                    >
                        {/* Keep the current role selectable even if it is not assignable (e.g. a non-admin viewing it) */}
                        {(isSelf ? roles : assignableRoles).map((role) => (
                            <option key={role.id} value={role.id}>
                                {role.name}
                                {role.admin ? " (full access)" : ""}
                            </option>
                        ))}
                    </select>
                    {fieldErrors.roleId && <p className="text-sm text-danger-fg-vivid">{fieldErrors.roleId}</p>}
                    {isSelf && <p className="text-xs text-fg-muted">You cannot change your own role.</p>}
                </div>

                {isNew ? (
                    <fieldset className="flex flex-col gap-2">
                        <legend className="mb-1 text-sm font-medium text-fg-secondary">Password</legend>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="radio" checked={generatePassword} onChange={() => setGeneratePassword(true)} />
                            Generate a secure password (recommended)
                        </label>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="radio" checked={!generatePassword} onChange={() => setGeneratePassword(false)} />
                            Set a password manually
                        </label>
                        {!generatePassword && (
                            <TextField
                                label="Initial password"
                                type="password"
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                autoComplete="new-password"
                                error={fieldErrors.password}
                            />
                        )}
                        <p className="text-xs text-fg-muted">The user will be asked to change it after the first login.</p>
                    </fieldset>
                ) : (
                    <label className="flex items-center gap-2 text-sm">
                        <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} disabled={isSelf} />
                        Active
                        <span className="text-xs text-fg-muted">
                            {isSelf ? "(you cannot deactivate yourself)" : "– deactivated users cannot log in and are logged out immediately"}
                        </span>
                    </label>
                )}

                <div className="mt-2 flex flex-wrap justify-end gap-3">
                    <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                        Cancel
                    </button>
                    <button type="submit" disabled={saving} className={buttonStyles.primary}>
                        {saving ? "Saving…" : isNew ? "Create user" : "Save"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
