import { Box } from "@mui/material";
import ProductCard from "./ProductCard";
import type { Product } from "../types/models";

interface ProductGridProps {
    products: Product[];
}

const ProductGrid = ({ products }: ProductGridProps) => (
    <Box
        sx={{
            display: "grid",
            gap: 3,
            gridTemplateColumns: {
                xs: "repeat(1, minmax(0, 1fr))",
                sm: "repeat(2, minmax(0, 1fr))",
                md: "repeat(3, minmax(0, 1fr))",
                lg: "repeat(4, minmax(0, 1fr))",
                xl: "repeat(5, minmax(0, 1fr))",
            },
            width: "100%",
        }}
    >
        {products.map((product) => (
            <ProductCard key={product.id} product={product} />
        ))}
    </Box>
);

export default ProductGrid;
