import { Card, CardContent, Typography } from "@mui/material";
import { LazyLoadImage } from "react-lazy-load-image-component";
import { Link } from "react-router-dom";
import type { Product } from "../types/models";

interface ProductCardProps {
    product: Product;
}

function ProductCard({ product }: ProductCardProps) {
    const primaryImage = product.images?.[0] || "";

    return (
        <Link to={`/product/${product.id}`} style={{ textDecoration: "none", color: "inherit" }}>
            <Card>
                <LazyLoadImage
                    alt={product.name}
                    src={primaryImage}
                    effect="blur"
                    width="100%"
                    height="180px"
                    style={{ objectFit: "cover", display: "block" }}
                />
                <CardContent>
                    <Typography variant="h6">{product.name}</Typography>
                    <Typography variant="body2" sx={{ minHeight: 40 }}>
                        {product.description}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        Category: {product.category}
                    </Typography>
                </CardContent>
            </Card>
        </Link>
    );
}

export default ProductCard;
