import { createContext } from "react";
import type { CurrentUser } from "../api/auth";

export interface AuthContextValue {
    /** The logged-in user, or null if not logged in. */
    user: CurrentUser | null;
    /** True while the initial session check is running. */
    loading: boolean;
    login: (username: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
    refreshUser: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
