import RoleRoute from "./RoleRoute";

export default function AdminRoute() {
    return <RoleRoute allowedRoles={["ROLE_ADMIN"]} />;
}
