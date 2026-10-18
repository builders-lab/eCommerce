import { useEffect, useState } from "react";
import type { ChangeEvent } from "react";
import { Box, Typography, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Button, Chip, CircularProgress, Alert, TextField } from "@mui/material";
import FlagIcon from "@mui/icons-material/Flag";
import DeleteIcon from "@mui/icons-material/Delete";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { getAllUsers, deleteUser, flagUser, unflagUser, updateUserDiscount, setEmployeeRole, extractApiErrorMessage } from "../../api/ApiService";
import { useAuth } from "../../global_component/auth-context";
import { hasRole } from "../../global_component/authUtils";
import type { User } from "../../types/models";

type EditMap = Record<number, string>;

function sortFlaggedFirst(data: User[]): User[] {
    return [...data].sort((a, b) => {
        const aFlagged = a.flagged || false;
        const bFlagged = b.flagged || false;
        return aFlagged === bFlagged ? 0 : aFlagged ? -1 : 1;
    });
}

export default function ManageUsers() {
    const [users, setUsers] = useState<User[]>([]);
    const [discountEdits, setDiscountEdits] = useState<EditMap>({});
    const [discountStartEdits, setDiscountStartEdits] = useState<EditMap>({});
    const [discountEndEdits, setDiscountEndEdits] = useState<EditMap>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { user: currentUser } = useAuth();

    const isAdmin = hasRole(currentUser, "ROLE_ADMIN");
    const isManager = hasRole(currentUser, "ROLE_MANAGER");

    const loadUsers = async ({ resetEdits, surfaceError }: { resetEdits: boolean; surfaceError: boolean }) => {
        setLoading(true);
        try {
            const data = await getAllUsers();
            setUsers(sortFlaggedFirst(Array.isArray(data) ? data : []));
            if (resetEdits) {
                setDiscountEdits({});
                setDiscountStartEdits({});
                setDiscountEndEdits({});
            }
            if (surfaceError) {
                setError(null);
            }
        } catch (err) {
            console.error("Failed to load users", err);
            if (surfaceError) {
                setError(extractApiErrorMessage(err, "Failed to fetch user list."));
            }
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadUsers({ resetEdits: false, surfaceError: true }).catch((e: unknown) => console.error("Load users failed", e));
    }, []);

    const handleDelete = async (id: number) => {
        if (!window.confirm("Are you sure? This deletes the user permanently.")) {
            return;
        }
        try {
            await deleteUser(id);
            await loadUsers({ resetEdits: true, surfaceError: false });
        } catch (err) {
            alert(extractApiErrorMessage(err, "Failed to delete user."));
        }
    };

    const handleFlag = async (id: number) => {
        try {
            await flagUser(id);
            await loadUsers({ resetEdits: true, surfaceError: false });
        } catch (err) {
            alert(extractApiErrorMessage(err, "Failed to flag user."));
        }
    };

    const handleUnflag = async (id: number) => {
        try {
            await unflagUser(id);
            await loadUsers({ resetEdits: true, surfaceError: false });
        } catch (err) {
            alert(extractApiErrorMessage(err, "Failed to unflag user."));
        }
    };

    const handleDiscountChange = (id: number, value: string) => {
        setDiscountEdits((prev) => ({ ...prev, [id]: value }));
    };

    const handleDiscountStartChange = (id: number, value: string) => {
        setDiscountStartEdits((prev) => ({ ...prev, [id]: value }));
    };

    const handleDiscountEndChange = (id: number, value: string) => {
        setDiscountEndEdits((prev) => ({ ...prev, [id]: value }));
    };

    const handleDiscountSave = async (id: number) => {
        const rawValue = discountEdits[id];
        const percentage = rawValue === "" || rawValue === undefined ? 0 : Number(rawValue);
        if (Number.isNaN(percentage) || percentage < 0 || percentage > 100) {
            alert("Discount must be a number between 0 and 100.");
            return;
        }
        const startDate = discountStartEdits[id];
        const endDate = discountEndEdits[id];
        if (percentage > 0 && !startDate) {
            alert("Start date is required for a discount.");
            return;
        }
        try {
            await updateUserDiscount(id, percentage, startDate || null, endDate || null);
            setDiscountEdits((prev) => {
                const next = { ...prev };
                delete next[id];
                return next;
            });
            setDiscountStartEdits((prev) => {
                const next = { ...prev };
                delete next[id];
                return next;
            });
            setDiscountEndEdits((prev) => {
                const next = { ...prev };
                delete next[id];
                return next;
            });
            await loadUsers({ resetEdits: true, surfaceError: false });
        } catch (err) {
            alert(extractApiErrorMessage(err, "Failed to update discount."));
        }
    };

    const handleEmployeeToggle = async (id: number, enabled: boolean) => {
        try {
            await setEmployeeRole(id, enabled);
            await loadUsers({ resetEdits: true, surfaceError: false });
        } catch (err) {
            alert(extractApiErrorMessage(err, "Failed to update employee role."));
        }
    };

    const getRoleNames = (roles?: string[]) => (Array.isArray(roles) ? roles : []);

    if (loading) {
        return <Box p={4}><CircularProgress /></Box>;
    }

    if (error) {
        return <Box p={4}><Alert severity="error">{error}</Alert></Box>;
    }

    return (
        <Box sx={{ p: 4 }}>
            <Typography variant="h4" gutterBottom>User Management</Typography>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>ID</TableCell>
                            <TableCell>Email</TableCell>
                            <TableCell>Username</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Roles</TableCell>
                            <TableCell>User Discount (%)</TableCell>
                            <TableCell>Discount Start</TableCell>
                            <TableCell>Discount End</TableCell>
                            <TableCell>Employee</TableCell>
                            <TableCell>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {users.map((u) => {
                            const isUserFlagged = Boolean(u.flagged);
                            const roleNames = getRoleNames(u.roles);
                            const isEmployee = roleNames.includes("ROLE_EMPLOYEE");
                            const discountValue = discountEdits[u.id] ?? String(u.userDiscountPercentage ?? 0);
                            const discountStartValue = discountStartEdits[u.id] ?? (u.userDiscountStartDate ?? "");
                            const discountEndValue = discountEndEdits[u.id] ?? (u.userDiscountEndDate ?? "");

                            return (
                                <TableRow key={u.id} sx={{ backgroundColor: isUserFlagged ? "#d32f2f" : "inherit" }}>
                                    <TableCell>{u.id}</TableCell>
                                    <TableCell>{u.email}</TableCell>
                                    <TableCell>{u.username || "N/A"}</TableCell>
                                    <TableCell>
                                        {isUserFlagged ? (
                                            <Chip icon={<FlagIcon />} label="Flagged" color="error" size="small" />
                                        ) : (
                                            <Chip label="Active" color="success" size="small" />
                                        )}
                                    </TableCell>
                                    <TableCell>{roleNames.length ? roleNames.join(", ") : "No Role"}</TableCell>
                                    <TableCell>
                                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                                            <TextField
                                                size="small"
                                                type="number"
                                                value={discountValue}
                                                onChange={(e: ChangeEvent<HTMLInputElement>) => handleDiscountChange(u.id, e.target.value)}
                                                inputProps={{ min: 0, max: 100, step: 1 }}
                                                sx={{ width: 90 }}
                                            />
                                            <Button size="small" variant="outlined" onClick={() => handleDiscountSave(u.id)}>
                                                Save
                                            </Button>
                                        </Box>
                                    </TableCell>
                                    <TableCell>
                                        <TextField
                                            size="small"
                                            type="date"
                                            value={discountStartValue}
                                            onChange={(e: ChangeEvent<HTMLInputElement>) => handleDiscountStartChange(u.id, e.target.value)}
                                            InputLabelProps={{ shrink: true }}
                                            sx={{ width: 150 }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <TextField
                                            size="small"
                                            type="date"
                                            value={discountEndValue}
                                            onChange={(e: ChangeEvent<HTMLInputElement>) => handleDiscountEndChange(u.id, e.target.value)}
                                            InputLabelProps={{ shrink: true }}
                                            sx={{ width: 150 }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Chip label={isEmployee ? "Employee" : "Not Employee"} color={isEmployee ? "success" : "default"} size="small" />
                                        {isAdmin && (
                                            <Button size="small" sx={{ ml: 1 }} onClick={() => handleEmployeeToggle(u.id, !isEmployee)}>
                                                {isEmployee ? "Remove" : "Make Employee"}
                                            </Button>
                                        )}
                                    </TableCell>
                                    <TableCell>
                                        {isManager && !isUserFlagged && (
                                            <Button size="small" color="warning" startIcon={<FlagIcon />} onClick={() => handleFlag(u.id)}>
                                                Flag
                                            </Button>
                                        )}

                                        {isAdmin && (
                                            <>
                                                {isUserFlagged && (
                                                    <Button size="small" color="success" startIcon={<CheckCircleIcon />} onClick={() => handleUnflag(u.id)} sx={{ mr: 1 }}>
                                                        Review OK
                                                    </Button>
                                                )}
                                                <Button size="small" color="error" startIcon={<DeleteIcon />} onClick={() => handleDelete(u.id)}>
                                                    Delete
                                                </Button>
                                            </>
                                        )}
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
