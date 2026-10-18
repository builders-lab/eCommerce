import { Box, Typography, Paper, Grid, Button } from "@mui/material";
import { useNavigate } from "react-router-dom";

export default function ManagerDashboard() {
    const navigate = useNavigate();

    return (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <Paper elevation={3} sx={{ p: 4, width: "100%", maxWidth: 900 }}>
                <Typography variant="h4" gutterBottom sx={{ fontWeight: "bold" }}>
                    Manager Dashboard
                </Typography>
                <Typography variant="body1" sx={{ mb: 3 }}>
                    Manage products and review users.
                </Typography>

                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Button fullWidth variant="contained" onClick={() => navigate("/manager/add-product")}>Add Product</Button>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Button fullWidth variant="contained" onClick={() => navigate("/manager/users")}>Manage Users</Button>
                    </Grid>
                </Grid>
            </Paper>
        </Box>
    );
}
