import { createTheme } from "@mui/material";

const theme = createTheme({
    palette: {
        mode: "dark",
        primary: { main: "#1976d2" },
        background: {
            default: "#121212",
            paper: "#1e1e1e",
        },
    },
});

export default theme;
