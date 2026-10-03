import { useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { useMaintenance } from "../maintenance/useMaintenance";
import { ADMIN_TABS } from "../pages/admin/adminTabs";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? "bg-slate-800 text-white" : "text-slate-300 hover:bg-slate-800/60 hover:text-white"}`;

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
            <header className="border-b border-slate-800 bg-slate-900">
                <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
                    <div className="flex items-center gap-6">
                        <Link to="/" className="text-lg font-semibold text-white">
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
                        <NavLink to="/account" className={navLinkClass}>
                            {user?.username}
                            {user?.role && <span className="ml-2 text-xs text-slate-400">{user.role.name}</span>}
                        </NavLink>
                        <button
                            type="button"
                            onClick={() => void logout()}
                            className="rounded-md px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/60 hover:text-white"
                        >
                            Log out
                        </button>
                    </div>
                </div>
            </header>

            {maintenance?.enabled && (
                <div className="border-b border-amber-700 bg-amber-900/60">
                    <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-amber-100">
                        <span>
                            <strong>Maintenance mode is active.</strong> Only administrators can use the application.
                        </span>
                        {hasPermission(user, "MAINTENANCE_MANAGE") && (
                            <Link to="/admin/maintenance" className="font-medium underline hover:text-white">
                                Maintenance settings
                            </Link>
                        )}
                    </div>
                </div>
            )}

            {showPasswordHint && (
                <div className="border-b border-amber-800 bg-amber-950/60">
                    <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-amber-200">
                        <span>You are using an initial password. We recommend changing it.</span>
                        <div className="flex gap-3">
                            <Link to="/account" className="font-medium underline hover:text-white">
                                Change password
                            </Link>
                            <button
                                type="button"
                                onClick={() => setPasswordHintDismissed(true)}
                                className="text-amber-300 hover:text-white"
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
