import { useEffect, useState } from "react";
import { Alert, Box, CircularProgress, Toolbar } from "@mui/material";
import Sidebar from "./SideBar";
import ProductGrid from "./ProductGrid";
import { fetchCategories, fetchProducts } from "../api/ApiService";
import type { Product } from "../types/models";

const drawerWidth = 240;

interface BodyProps {
    drawerOpen: boolean;
    toggleDrawer: (nextState?: boolean) => void;
}

function Body({ drawerOpen, toggleDrawer }: BodyProps) {
    const [categories, setCategories] = useState<string[]>([]);
    const [selectedCategory, setSelectedCategory] = useState("All");
    const [products, setProducts] = useState<Product[]>([]);
    const [loadingProducts, setLoadingProducts] = useState(false);
    const [productsError, setProductsError] = useState("");

    useEffect(() => {
        const loadCategories = async () => {
            try {
                const data = await fetchCategories();
                setCategories(data);
            } catch (err) {
                console.error("Failed to load categories", err);
                setCategories(["All"]);
            }
        };
        loadCategories().catch((error: unknown) => console.error("Category load failed", error));
    }, []);

    useEffect(() => {
        const loadProducts = async () => {
            setLoadingProducts(true);
            setProductsError("");
            try {
                const data = await fetchProducts(selectedCategory);
                setProducts(data);
            } catch (err) {
                console.error("Failed to load products", err);
                setProducts([]);
                setProductsError("Failed to load products. Please try again.");
            } finally {
                setLoadingProducts(false);
            }
        };
        loadProducts().catch((error: unknown) => console.error("Product load failed", error));
    }, [selectedCategory]);

    return (
        <Box sx={{ display: "flex" }}>
            <Sidebar
                drawerOpen={drawerOpen}
                toggleDrawer={toggleDrawer}
                categories={categories}
                selectedCategory={selectedCategory}
                setSelectedCategory={setSelectedCategory}
            />

            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    p: 3,
                    width: drawerOpen ? `calc(100% - ${drawerWidth}px)` : "100%",
                    transition: "width 0.3s ease",
                }}
            >
                <Toolbar />
                {loadingProducts && <CircularProgress />}
                {!loadingProducts && productsError && <Alert severity="error" sx={{ mb: 2 }}>{productsError}</Alert>}
                {!loadingProducts && !productsError && products.length === 0 && (
                    <Alert severity="info" sx={{ mb: 2 }}>No products available.</Alert>
                )}
                {!loadingProducts && !productsError && <ProductGrid products={products} />}
            </Box>
        </Box>
    );
}

export default Body;
