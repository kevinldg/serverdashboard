import { Navigate, NavLink, Outlet, useLocation } from "react-router-dom";
import { hasPermission } from "../../auth/permissions";
import { useAuth } from "../../auth/useAuth";
import { ADMIN_TABS } from "./adminTabs";

export function AdminLayout() {
    const { user } = useAuth();
    const location = useLocation();
    const tabs = ADMIN_TABS.filter((tab) => hasPermission(user, tab.permission));

    if (tabs.length === 0) {
        return <p className="text-slate-400">You do not have access to the admin area.</p>;
    }
    // "/admin" opens the first tab the user may see
    if (location.pathname.replace(/\/$/, "") === "/admin") {
        return <Navigate to={tabs[0].path} replace />;
    }

    return (
        <div className="flex flex-col gap-6">
            <h1 className="text-2xl font-semibold text-white">Administration</h1>
            <nav className="flex gap-1 border-b border-slate-800">
                {tabs.map((tab) => (
                    <NavLink
                        key={tab.path}
                        to={tab.path}
                        className={({ isActive }) =>
                            `-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
                                isActive ? "border-sky-500 text-white" : "border-transparent text-slate-400 hover:text-slate-200"
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
