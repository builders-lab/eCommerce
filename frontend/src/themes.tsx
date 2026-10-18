import { CssBaseline, ThemeProvider } from "@mui/material";
import type { PropsWithChildren } from "react";
import theme from "./theme";

export function AppThemeProvider({ children }: PropsWithChildren) {
    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            {children}
        </ThemeProvider>
    );
}
