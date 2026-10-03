import {Navigate, Route, Routes} from "react-router-dom";
import {useAuth} from "./auth/useAuth";
import {useMaintenance} from "./maintenance/useMaintenance";
import {RequireAuth} from "./auth/RequireAuth";
import {RequirePermission} from "./auth/RequirePermission";
import {AppLayout} from "./components/AppLayout";
import {AccountPage} from "./pages/AccountPage";
import {AdminLayout} from "./pages/admin/AdminLayout";
import {AnnouncementsPage} from "./pages/admin/AnnouncementsPage";
import {RolesPage} from "./pages/admin/RolesPage";
import {UsersPage} from "./pages/admin/UsersPage";
import {ContainerDetailsPage} from "./pages/ContainerDetailsPage";
import {CreateContainerPage} from "./pages/create/CreateContainerPage";
import {DashboardPage} from "./pages/DashboardPage";
import {LoginPage} from "./pages/LoginPage";
import {MaintenancePage} from "./pages/MaintenancePage";
import {MaintenanceSettingsPage} from "./pages/admin/MaintenanceSettingsPage";

export default function App() {
    const {user, loading} = useAuth();
    const {status: maintenance} = useMaintenance();

    if (loading || maintenance === null) {
        return <div className="flex min-h-screen items-center justify-center text-slate-400">Loading…</div>;
    }

    // During maintenance, only administrators can use the application; the login stays reachable for them.
    if (maintenance.enabled && !user?.admin) {
        return (
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="*" element={<MaintenancePage/>}/>
            </Routes>
        );
    }

    return (
        <Routes>
            <Route path="/login" element={<LoginPage/>}/>
            <Route element={<RequireAuth/>}>
                <Route element={<AppLayout/>}>
                    <Route index element={<DashboardPage/>}/>
                    <Route path="containers/new" element={<RequirePermission permission="CONTAINER_CREATE"><CreateContainerPage/></RequirePermission>}/>
                    <Route path="containers/:id" element={<ContainerDetailsPage/>}/>
                    <Route path="account" element={<AccountPage/>}/>
                    <Route path="admin" element={<AdminLayout/>}>
                        <Route path="users" element={<RequirePermission permission="USER_MANAGE"><UsersPage/></RequirePermission>}/>
                        <Route path="roles" element={<RequirePermission permission="ROLE_MANAGE"><RolesPage/></RequirePermission>}/>
                        <Route path="announcements" element={<RequirePermission permission="ANNOUNCEMENT_MANAGE"><AnnouncementsPage/></RequirePermission>}/>
                        <Route path="maintenance" element={<RequirePermission permission="MAINTENANCE_MANAGE"><MaintenanceSettingsPage/></RequirePermission>}/>
                    </Route>
                </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
    );
}
