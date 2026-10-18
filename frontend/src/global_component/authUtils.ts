import type { NavigateFunction } from "react-router-dom";
import type { User } from "../types/models";

export function buildLoginRedirectPath(target: string): string {
    return `/login?redirect=${encodeURIComponent(target)}`;
}

export function redirectToLogin(navigate: NavigateFunction, target: string): void {
    navigate(buildLoginRedirectPath(target));
}

export function hasRole(user: User | null | undefined, role: string): boolean {
    return Boolean(user?.roles?.includes(role));
}

export function hasAnyRole(user: User | null | undefined, roles: readonly string[]): boolean {
    return roles.some((role) => hasRole(user, role));
}
