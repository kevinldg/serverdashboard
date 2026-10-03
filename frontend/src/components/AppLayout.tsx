import { useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { useMaintenance } from "../maintenance/useMaintenance";
import { ADMIN_TABS } from "../pages/admin/adminTabs";
import { ThemeToggle } from "./ThemeToggle";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? "bg-raised text-fg-strong" : "text-fg-secondary hover:bg-raised/60 hover:text-fg-strong"}`;

/** Page frame for logged-in users: header with navigation and user menu. */
export function AppLayout() {
    const { user, logout } = useAuth();
    const location = useLocation();
    const [passwordHintDismissed, setPasswordHintDismissed] = useState(false);
    const { status: maintenance } = useMaintenance();

    const showAdminLink = ADMIN_TABS.some((tab) => hasPermission(user, tab.permission));
    const showPasswordHint =
        user?.passwordChangeRecommended && !passwordHintDismissed && location.pathname !== "/account";

    return (
        <div className="min-h-screen">
            <header className="border-b border-line bg-surface">
                <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
                    <div className="flex items-center gap-6">
                        <Link to="/" className="text-lg font-semibold text-fg-strong">
                            ServerDashboard
                        </Link>
                        <nav className="flex gap-1">
                            <NavLink to="/" end className={navLinkClass}>
                                Dashboard
                            </NavLink>
                            {showAdminLink && (
                                <NavLink to="/admin" className={navLinkClass}>
                                    Admin
                                </NavLink>
                            )}
                        </nav>
                    </div>
                    <div className="flex items-center gap-2">
                        <ThemeToggle />
                        <NavLink to="/account" className={navLinkClass}>
                            {user?.username}
                            {user?.role && <span className="ml-2 text-xs text-fg-muted">{user.role.name}</span>}
                        </NavLink>
                        <button
                            type="button"
                            onClick={() => void logout()}
                            className="rounded-md px-3 py-2 text-sm font-medium text-fg-secondary hover:bg-raised/60 hover:text-fg-strong"
                        >
                            Log out
                        </button>
                    </div>
                </div>
            </header>

            {maintenance?.enabled && (
                <div className="border-b border-warning-line bg-warning-soft/60">
                    <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-warning-fg-strong">
                        <span>
                            <strong>Maintenance mode is active.</strong> Only administrators can use the application.
                        </span>
                        {hasPermission(user, "MAINTENANCE_MANAGE") && (
                            <Link to="/admin/maintenance" className="font-medium underline hover:text-fg-strong">
                                Maintenance settings
                            </Link>
                        )}
                    </div>
                </div>
            )}

            {showPasswordHint && (
                <div className="border-b border-warning-line bg-warning-soft/60">
                    <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-warning-fg-strong">
                        <span>You are using an initial password. We recommend changing it.</span>
                        <div className="flex gap-3">
                            <Link to="/account" className="font-medium underline hover:text-fg-strong">
                                Change password
                            </Link>
                            <button
                                type="button"
                                onClick={() => setPasswordHintDismissed(true)}
                                className="text-warning-fg hover:text-fg-strong"
                            >
                                Dismiss
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <main className="mx-auto max-w-7xl px-4 py-8">
                <Outlet />
            </main>
        </div>
    );
}
