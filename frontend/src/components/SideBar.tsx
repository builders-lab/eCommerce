import {
    Drawer,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    Divider,
    IconButton,
    Toolbar,
    useMediaQuery,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useTheme } from "@mui/material/styles";

const drawerWidth = 240;

interface SidebarProps {
    drawerOpen: boolean;
    toggleDrawer: (nextState?: boolean) => void;
    categories: string[];
    selectedCategory: string;
    setSelectedCategory: (value: string) => void;
}

const Sidebar = ({ drawerOpen, toggleDrawer, categories, selectedCategory, setSelectedCategory }: SidebarProps) => {
    const theme = useTheme();
    const isSmall = useMediaQuery(theme.breakpoints.down("md"));

    const handleClose = () => toggleDrawer(false);

    return (
        <Drawer
            variant={isSmall ? "temporary" : "persistent"}
            anchor="left"
            open={drawerOpen}
            onClose={handleClose}
            ModalProps={{ keepMounted: true }}
            sx={{
                width: drawerOpen ? drawerWidth : 0,
                flexShrink: 0,
                "& .MuiDrawer-paper": {
                    width: drawerWidth,
                    boxSizing: "border-box",
                    transition: "transform 225ms ease, width 0.3s ease",
                    overflowX: "hidden",
                },
            }}
        >
            <Toolbar sx={{ justifyContent: "flex-end" }}>
                <IconButton aria-label="close sidebar" onClick={handleClose}>
                    <CloseIcon />
                </IconButton>
            </Toolbar>
            <Divider />
            <List>
                <ListItem disablePadding>
                    <ListItemButton
                        onClick={() => {
                            setSelectedCategory("All");
                            if (isSmall) {
                                handleClose();
                            }
                        }}
                        selected={selectedCategory === "All"}
                    >
                        <ListItemText primary="All Categories" />
                    </ListItemButton>
                </ListItem>
                <Divider />
                {categories.map((category) => (
                    <ListItem key={category} disablePadding>
                        <ListItemButton
                            onClick={() => {
                                setSelectedCategory(category);
                                if (isSmall) {
                                    handleClose();
                                }
                            }}
                            selected={selectedCategory === category}
                        >
                            <ListItemText primary={category} />
                        </ListItemButton>
                    </ListItem>
                ))}
                {!isSmall && (
                    <>
                        <Divider />
                        <ListItem disablePadding>
                            <ListItemButton onClick={handleClose}>
                                <ListItemText primary="Close" />
                            </ListItemButton>
                        </ListItem>
                    </>
                )}
            </List>
        </Drawer>
    );
};

export default Sidebar;
