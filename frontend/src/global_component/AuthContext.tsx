import { useCallback, useEffect, useMemo, useState } from "react";
import type { PropsWithChildren } from "react";
import ApiService from "../api/ApiService";
import type { User } from "../types/models";
import { AuthContext } from "./auth-context";
import type { AuthContextValue } from "./auth-context";

function isStoredUser(value: unknown): value is User {
    if (!value || typeof value !== "object") {
        return false;
    }

    const candidate = value as Partial<User>;
    return typeof candidate.email === "string";
}

export function AuthProvider({ children }: PropsWithChildren) {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const initializeAuth = () => {
            const token = localStorage.getItem("token");
            const storedUser = localStorage.getItem("user");

            if (token && storedUser && storedUser !== "undefined") {
                try {
                    const parsedUser: unknown = JSON.parse(storedUser);
                    if (!isStoredUser(parsedUser)) {
                        throw new Error("Stored user payload is invalid");
                    }
                    ApiService.setAuthToken(token);
                    setIsAuthenticated(true);
                    setUser(parsedUser);
                } catch (error) {
                    console.error("Corrupted auth data found, clearing...", error);
                    localStorage.removeItem("token");
                    localStorage.removeItem("user");
                    ApiService.clearAuthToken();
                }
            } else {
                ApiService.clearAuthToken();
            }
            setLoading(false);
        };

        initializeAuth();
    }, []);

    const login = useCallback((token: string, userData: User) => {
        localStorage.setItem("token", token);
        localStorage.setItem("user", JSON.stringify(userData));
        ApiService.setAuthToken(token);
        setIsAuthenticated(true);
        setUser(userData);
    }, []);

    const logout = useCallback(() => {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        ApiService.clearAuthToken();
        setIsAuthenticated(false);
        setUser(null);
    }, []);

    useEffect(() => {
        ApiService.setUnauthorizedHandler(logout);
        return () => ApiService.setUnauthorizedHandler(null);
    }, [logout]);

    const value = useMemo<AuthContextValue>(
        () => ({ isAuthenticated, user, login, logout }),
        [isAuthenticated, user, login, logout],
    );

    if (loading) {
        return (
            <div style={{ display: "flex", justifyContent: "center", marginTop: "50px" }}>
                Loading Application...
            </div>
        );
    }

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
