import { useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import { useAuth } from "../global_component/auth-context";
import ApiService, { extractApiErrorMessage } from "../api/ApiService";
import { Box, Button, TextField, Typography, Paper, Alert, Container } from "@mui/material";
import type { LoginRequest } from "../types/models";

export default function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login } = useAuth();

    const [formData, setFormData] = useState<LoginRequest>({ email: "", password: "" });
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const from = new URLSearchParams(location.search).get("redirect") || "/";

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setError("");
        setLoading(true);

        try {
            const response = await ApiService.login(formData);
            if (!response.token || !response.user) {
                throw new Error("Invalid server response: Missing token or user data.");
            }

            login(response.token, response.user);
            const redirectPath = decodeURIComponent(from);
            navigate(redirectPath, { replace: true });
        } catch (err) {
            console.error("Login Error:", err);
            setError(extractApiErrorMessage(err, "Invalid email or password"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Container component="main" maxWidth="xs">
            <Box
                sx={{
                    marginTop: 8,
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                }}
            >
                <Paper elevation={3} sx={{ p: 4, width: "100%" }}>
                    <Typography component="h1" variant="h5" align="center" gutterBottom>
                        Sign In
                    </Typography>

                    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

                    <form onSubmit={handleSubmit}>
                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            id="email"
                            label="Email Address"
                            name="email"
                            autoComplete="email"
                            autoFocus
                            value={formData.email}
                            onChange={handleChange}
                        />
                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            name="password"
                            label="Password"
                            type="password"
                            id="password"
                            autoComplete="current-password"
                            value={formData.password}
                            onChange={handleChange}
                        />

                        <Button type="submit" fullWidth variant="contained" sx={{ mt: 3, mb: 2 }} disabled={loading}>
                            {loading ? "Signing in..." : "Sign In"}
                        </Button>

                        <Box display="flex" justifyContent="center">
                            <Link to="/signup" style={{ textDecoration: "none", color: "#1976d2" }}>
                                {"Don't have an account? Sign Up"}
                            </Link>
                        </Box>
                    </form>
                </Paper>
            </Box>
        </Container>
    );
}
