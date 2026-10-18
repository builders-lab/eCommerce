import { AppBar, Toolbar, Typography, Button, IconButton, Badge, Box } from "@mui/material";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import MenuIcon from "@mui/icons-material/Menu";
import { useNavigate } from "react-router-dom";
import { useCart } from "../global_component/cart-context";
import { useAuth } from "../global_component/auth-context";

interface HeaderProps {
    onMenuClick: (nextState?: boolean) => void;
}

export default function Header({ onMenuClick }: HeaderProps) {
    const navigate = useNavigate();
    const { cartCount } = useCart();
    const { isAuthenticated, logout, user } = useAuth();

    const handleLoginLogout = () => {
        if (isAuthenticated) {
            logout();
            navigate("/");
            return;
        }
        navigate("/login");
    };

    return (
        <AppBar position="static">
            <Toolbar>
                <IconButton color="inherit" edge="start" sx={{ mr: 1 }} onClick={() => onMenuClick()}>
                    <MenuIcon />
                </IconButton>

                <Typography variant="h6" sx={{ flexGrow: 1, cursor: "pointer" }} onClick={() => navigate("/")}>
                    E-Commerce
                </Typography>

                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    {isAuthenticated && user && (
                        <Typography variant="body2" sx={{ mr: 1, display: { xs: "none", sm: "block" } }}>
                            Hello, {user.username || user.email}
                        </Typography>
                    )}

                    <IconButton color="inherit" onClick={() => navigate("/checkout")}>
                        <Badge badgeContent={cartCount} color="error">
                            <ShoppingCartIcon />
                        </Badge>
                    </IconButton>

                    <Button color="inherit" onClick={handleLoginLogout}>
                        {isAuthenticated ? "Logout" : "Login"}
                    </Button>
                </Box>
            </Toolbar>
        </AppBar>
    );
}
