import { useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { Box, TextField, Button, Typography, Paper } from "@mui/material";
import { useNavigate } from "react-router-dom";
import apiService, { extractApiErrorMessage } from "../api/ApiService";

interface SignUpForm {
    username: string;
    email: string;
    password: string;
    confirmPassword: string;
    captcha: string;
}

function SignUp() {
    const [formData, setFormData] = useState<SignUpForm>({
        username: "",
        email: "",
        password: "",
        confirmPassword: "",
        captcha: "",
    });

    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setError("");

        if (formData.password !== formData.confirmPassword) {
            setError("Passwords do not match.");
            return;
        }
        if (formData.captcha !== "1234") {
            setError("Invalid captcha. Please enter 1234 for demo.");
            return;
        }

        try {
            setSubmitting(true);
            await apiService.signup({
                username: formData.username,
                email: formData.email,
                password: formData.password,
            });

            navigate("/login");
        } catch (err) {
            console.error("Signup failed:", err);
            setError(extractApiErrorMessage(err, "Signup failed. Please try again."));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Box
            sx={{
                position: "fixed",
                inset: 0,
                zIndex: (theme) => theme.zIndex.appBar - 1,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                width: "100vw",
                height: "100vh",
                overflowY: "auto",
                bgcolor: "#f4f6f8",
            }}
        >
            <Paper elevation={3} sx={{ p: 4, width: 350, maxWidth: "90vw" }}>
                <Typography variant="h5" align="center" gutterBottom>
                    Sign Up
                </Typography>
                <form onSubmit={handleSubmit}>
                    <TextField label="Username" name="username" fullWidth margin="normal" required value={formData.username} onChange={handleChange} />
                    <TextField label="Email" name="email" type="email" fullWidth margin="normal" required value={formData.email} onChange={handleChange} />
                    <TextField label="Password" name="password" type="password" fullWidth margin="normal" required value={formData.password} onChange={handleChange} />
                    <TextField label="Confirm Password" name="confirmPassword" type="password" fullWidth margin="normal" required value={formData.confirmPassword} onChange={handleChange} />
                    <TextField label="Captcha" name="captcha" fullWidth margin="normal" required value={formData.captcha} onChange={handleChange} />
                    <Typography variant="caption" sx={{ color: "gray" }}>
                        Enter "1234" for demo
                    </Typography>

                    {error && (
                        <Typography color="error" variant="body2" sx={{ mt: 1 }}>
                            {error}
                        </Typography>
                    )}

                    <Button type="submit" fullWidth variant="contained" sx={{ mt: 2 }} disabled={submitting}>
                        {submitting ? "Signing up..." : "Sign Up"}
                    </Button>
                </form>
            </Paper>
        </Box>
    );
}

export default SignUp;
