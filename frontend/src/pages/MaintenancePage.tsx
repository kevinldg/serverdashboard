import { Link } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { buttonStyles } from "../components/buttonStyles";
import { useMaintenance } from "../maintenance/useMaintenance";

/** Shown to everyone except administrators while maintenance mode is active. */
export function MaintenancePage() {
    const { status, refresh } = useMaintenance();
    const { user, logout } = useAuth();

    return (
        <div className="flex min-h-screen items-center justify-center px-4">
            <div className="w-full max-w-lg rounded-lg border border-amber-800 bg-slate-900 p-8 text-center">
                <h1 className="text-2xl font-semibold text-white">Under maintenance</h1>
                <p className="mt-4 whitespace-pre-line text-slate-300">
                    {status?.message || "The application is currently unavailable due to maintenance. Please try again later."}
                </p>
                <div className="mt-8 flex flex-col items-center gap-3">
                    <button type="button" onClick={() => void refresh()} className={buttonStyles.secondary}>
                        Check again
                    </button>
                    {user ? (
                        <button type="button" onClick={() => void logout()} className="text-sm text-sky-400 hover:underline">
                            Log out ({user.username})
                        </button>
                    ) : (
                        <Link to="/login" className="text-sm text-slate-400 hover:text-sky-400 hover:underline">
                            Administrator login
                        </Link>
                    )}
                </div>
            </div>
        </div>
    );
}
