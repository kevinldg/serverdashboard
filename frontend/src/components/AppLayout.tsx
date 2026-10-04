import { useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { hasPermission } from "../auth/permissions";
import { useAuth } from "../auth/useAuth";
import { useMaintenance } from "../maintenance/useMaintenance";
import { ADMIN_TABS } from "../pages/admin/adminTabs";
import { ThemeToggle } from "./ThemeToggle";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? "bg-raised text-fg-strong" : "text-fg-secondary hover:bg-raised/60 hover:text-fg-strong"}`;

const navButtonClass = "rounded-md px-3 py-2 text-sm font-medium text-fg-secondary hover:bg-raised/60 hover:text-fg-strong";

/** Page frame for logged-in users: header with navigation and user menu (a collapsible menu on small screens). */
export function AppLayout() {
    const { user, logout } = useAuth();
    const location = useLocation();
    const [passwordHintDismissed, setPasswordHintDismissed] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const { status: maintenance } = useMaintenance();

    const closeMenu = () => setMenuOpen(false);
    const showAdminLink = ADMIN_TABS.some((tab) => hasPermission(user, tab.permission));
    const showPasswordHint =
        user?.passwordChangeRecommended && !passwordHintDismissed && location.pathname !== "/account";

    return (
        <div className="min-h-screen">
            <header className="border-b border-line bg-surface">
                <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
                    <div className="flex items-center gap-6">
                        <Link to="/" onClick={closeMenu} className="text-lg font-semibold text-fg-strong">
                            ServerDashboard
                        </Link>
                        <nav className="hidden gap-1 md:flex">
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
                        <div className="hidden items-center gap-2 md:flex">
                            <NavLink to="/account" className={navLinkClass}>
                                {user?.username}
                                {user?.role && <span className="ml-2 text-xs text-fg-muted">{user.role.name}</span>}
                            </NavLink>
                            <button type="button" onClick={() => void logout()} className={navButtonClass}>
                                Log out
                            </button>
                        </div>
                        <button
                            type="button"
                            onClick={() => setMenuOpen((open) => !open)}
                            aria-expanded={menuOpen}
                            aria-controls="mobile-menu"
                            aria-label={menuOpen ? "Close menu" : "Open menu"}
                            className="rounded-md p-2 text-fg-secondary hover:bg-raised/60 hover:text-fg-strong md:hidden"
                        >
                            {menuOpen ? <CloseIcon /> : <MenuIcon />}
                        </button>
                    </div>
                </div>

                {/* Navigation for small screens; the links close the menu */}
                {menuOpen && (
                    <nav id="mobile-menu" className="flex flex-col gap-1 border-t border-line px-4 py-3 md:hidden">
                        <NavLink to="/" end className={navLinkClass} onClick={closeMenu}>
                            Dashboard
                        </NavLink>
                        {showAdminLink && (
                            <NavLink to="/admin" className={navLinkClass} onClick={closeMenu}>
                                Admin
                            </NavLink>
                        )}
                        <NavLink to="/account" className={navLinkClass} onClick={closeMenu}>
                            {user?.username}
                            {user?.role && <span className="ml-2 text-xs text-fg-muted">{user.role.name}</span>}
                        </NavLink>
                        <button type="button" onClick={() => void logout()} className={`${navButtonClass} text-left`}>
                            Log out
                        </button>
                    </nav>
                )}
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

            <main className="mx-auto max-w-7xl px-4 py-6 sm:py-8">
                <Outlet />
            </main>
        </div>
    );
}

function MenuIcon() {
    return (
        <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
    );
}

function CloseIcon() {
    return (
        <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
        </svg>
    );
}
