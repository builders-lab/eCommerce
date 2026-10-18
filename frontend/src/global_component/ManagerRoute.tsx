import RoleRoute from "./RoleRoute";

export default function ManagerRoute() {
    return <RoleRoute allowedRoles={["ROLE_ADMIN", "ROLE_MANAGER"]} />;
}
