import { useState } from "react";
import type { FormEvent, ChangeEvent } from "react";
import { Box, TextField, Button, Typography, Paper, Alert } from "@mui/material";
import { registerManager, extractApiErrorMessage } from "../../api/ApiService";

interface ManagerForm {
    email: string;
    password: string;
    username: string;
}

type MessageState = { type: "success" | "error"; text: string } | null;

export default function AddManager() {
    const [formData, setFormData] = useState<ManagerForm>({ email: "", password: "", username: "" });
    const [msg, setMsg] = useState<MessageState>(null);

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        try {
            await registerManager(formData);
            setMsg({ type: "success", text: "Manager account created successfully!" });
            setFormData({ email: "", password: "", username: "" });
        } catch (error) {
            console.error("Manager creation failed", error);
            setMsg({ type: "error", text: extractApiErrorMessage(error, "Failed to create manager.") });
        }
    };

    return (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <Paper sx={{ p: 4, width: 400 }}>
                <Typography variant="h5" mb={2}>Create Manager Account</Typography>
                {msg && <Alert severity={msg.type} sx={{ mb: 2 }}>{msg.text}</Alert>}
                <form onSubmit={handleSubmit}>
                    <TextField fullWidth label="Username" margin="normal" name="username" value={formData.username} onChange={handleChange} />
                    <TextField fullWidth label="Email" margin="normal" name="email" value={formData.email} onChange={handleChange} />
                    <TextField fullWidth label="Password" type="password" margin="normal" name="password" value={formData.password} onChange={handleChange} />
                    <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }}>
                        Create Manager
                    </Button>
                </form>
            </Paper>
        </Box>
    );
}
