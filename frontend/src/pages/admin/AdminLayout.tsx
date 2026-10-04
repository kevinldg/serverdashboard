import { Navigate, NavLink, Outlet, useLocation } from "react-router-dom";
import { hasPermission } from "../../auth/permissions";
import { useAuth } from "../../auth/useAuth";
import { ADMIN_TABS } from "./adminTabs";

export function AdminLayout() {
    const { user } = useAuth();
    const location = useLocation();
    const tabs = ADMIN_TABS.filter((tab) => hasPermission(user, tab.permission));

    if (tabs.length === 0) {
        return <p className="text-fg-muted">You do not have access to the admin area.</p>;
    }
    // "/admin" opens the first tab the user may see
    if (location.pathname.replace(/\/$/, "") === "/admin") {
        return <Navigate to={tabs[0].path} replace />;
    }

    return (
        <div className="flex flex-col gap-6">
            <h1 className="text-2xl font-semibold text-fg-strong">Administration</h1>
            {/* Scrolls sideways when the tabs do not fit; the bottom line is a shadow so overflow does not clip the active tab's border */}
            <nav className="flex gap-1 overflow-x-auto shadow-[inset_0_-1px_0_var(--color-line)]">
                {tabs.map((tab) => (
                    <NavLink
                        key={tab.path}
                        to={tab.path}
                        className={({ isActive }) =>
                            `shrink-0 border-b-2 px-4 py-2 text-sm font-medium whitespace-nowrap ${
                                isActive ? "border-sky-500 text-fg-strong" : "border-transparent text-fg-muted hover:text-fg"
                            }`
                        }
                    >
                        {tab.label}
                    </NavLink>
                ))}
            </nav>
            <Outlet />
        </div>
    );
}
