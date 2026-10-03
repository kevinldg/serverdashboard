import axios from "axios";
import { type ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import * as authApi from "../api/auth";
import type { CurrentUser } from "../api/auth";
import { apiClient } from "../api/client";
import { AuthContext, type AuthContextValue } from "./AuthContext";

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<CurrentUser | null>(null);
    const [loading, setLoading] = useState(true);

    // Any 401 means the session is gone (expired, logged out elsewhere, or user deactivated).
    useEffect(() => {
        const interceptor = apiClient.interceptors.response.use(undefined, (error: unknown) => {
            if (axios.isAxiosError(error) && error.response?.status === 401) {
                setUser(null);
            }
            return Promise.reject(error);
        });
        return () => apiClient.interceptors.response.eject(interceptor);
    }, []);

    // Restore an existing session on page load.
    useEffect(() => {
        let cancelled = false;
        authApi.getCurrentUser()
            .then((currentUser) => {
                if (!cancelled) setUser(currentUser);
            })
            .catch(() => {
                if (!cancelled) setUser(null);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, []);

    const login = useCallback(async (username: string, password: string) => {
        setUser(await authApi.login(username, password));
    }, []);

    const logout = useCallback(async () => {
        try {
            await authApi.logout();
        } finally {
            setUser(null);
        }
    }, []);

    const refreshUser = useCallback(async () => {
        setUser(await authApi.getCurrentUser());
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({ user, loading, login, logout, refreshUser }),
        [user, loading, login, logout, refreshUser],
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
