import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./auth-context";
import { buildLoginRedirectPath, hasAnyRole } from "./authUtils";

interface RoleRouteProps {
    allowedRoles: string[];
}

export default function RoleRoute({ allowedRoles }: RoleRouteProps) {
    const { isAuthenticated, user } = useAuth();
    const location = useLocation();

    if (!isAuthenticated) {
        const redirectTarget = `${location.pathname}${location.search}`;
        return <Navigate to={buildLoginRedirectPath(redirectTarget)} replace />;
    }

    const hasRequiredRole = hasAnyRole(user, allowedRoles);
    if (!hasRequiredRole) {
        return <Navigate to="/" replace />;
    }

    return <Outlet />;
}
