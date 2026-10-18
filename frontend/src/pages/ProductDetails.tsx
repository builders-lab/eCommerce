import { useEffect, useMemo, useState } from "react";
import type { ChangeEvent } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Typography, Button, Grid, Card, CardMedia, Rating, Stack, Radio, RadioGroup, FormControlLabel, FormControl, FormLabel, Chip } from "@mui/material";
import AddShoppingCartIcon from "@mui/icons-material/AddShoppingCart";
import FlashOnIcon from "@mui/icons-material/FlashOn";
import LocalOfferIcon from "@mui/icons-material/LocalOffer";

import { fetchProduct, fetchReviews } from "../api/ApiService";
import { useCart } from "../global_component/cart-context";
import { useAuth } from "../global_component/auth-context";
import { redirectToLogin } from "../global_component/authUtils";
import type { Discount, Product, Review } from "../types/models";

const currency = new Intl.NumberFormat(undefined, { style: "currency", currency: "USD" });

function isDiscountActive(discount: Discount): boolean {
    const today = new Date();
    const start = discount.startDate ? new Date(`${discount.startDate}T00:00:00`) : null;
    const end = discount.endDate ? new Date(`${discount.endDate}T23:59:59`) : null;
    if (start && start > today) {
        return false;
    }
    if (end && end < today) {
        return false;
    }
    return true;
}

function getBestDiscount(discounts: Discount[]): Discount | null {
    const activeDiscounts = discounts.filter(isDiscountActive);
    if (!activeDiscounts.length) {
        return null;
    }
    return activeDiscounts.reduce((best, current) => (current.percentage > best.percentage ? current : best));
}

function ProductDetails() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [product, setProduct] = useState<Product | null>(null);
    const [reviews, setReviews] = useState<Review[]>([]);
    const [selectedDiscount, setSelectedDiscount] = useState<Discount | null>(null);
    const [hasUserSelectedDiscount, setHasUserSelectedDiscount] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState("");

    const { addToCart } = useCart();
    const { isAuthenticated, user } = useAuth();

    useEffect(() => {
        const load = async () => {
            setIsLoading(true);
            setLoadError("");
            try {
                const [productData, reviewData] = await Promise.all([fetchProduct(id), fetchReviews(id)]);
                if (!productData) {
                    setProduct(null);
                    setReviews([]);
                    setLoadError("Product could not be loaded.");
                    return;
                }

                setProduct(productData);
                setReviews(reviewData);
                setSelectedDiscount(null);
                setHasUserSelectedDiscount(false);
            } catch (error) {
                console.error("Failed to load product details", error);
                setProduct(null);
                setReviews([]);
                setLoadError("Product could not be loaded.");
            } finally {
                setIsLoading(false);
            }
        };

        load().catch((error: unknown) => console.error("Failed to load product details", error));
    }, [id]);

    useEffect(() => {
        if (!product?.discounts?.length || selectedDiscount || hasUserSelectedDiscount) {
            return;
        }

        const bestDiscount = getBestDiscount(product.discounts);
        if (bestDiscount) {
            setSelectedDiscount(bestDiscount);
        }
    }, [product, selectedDiscount, hasUserSelectedDiscount]);

    const activeDiscounts = useMemo(() => (product?.discounts ? product.discounts.filter(isDiscountActive) : []), [product]);

    const calculatePrice = (): number => {
        if (!product) {
            return 0;
        }
        if (!selectedDiscount) {
            return Number(product.price);
        }

        const discountAmount = (Number(product.price) * selectedDiscount.percentage) / 100;
        return Number((Number(product.price) - discountAmount).toFixed(2));
    };

    const isUserDiscountActive = (): boolean => {
        const percentage = user?.userDiscountPercentage || 0;
        if (percentage <= 0 || !user?.userDiscountStartDate) {
            return false;
        }

        const today = new Date();
        const start = new Date(`${user.userDiscountStartDate}T00:00:00`);
        const end = user.userDiscountEndDate ? new Date(`${user.userDiscountEndDate}T23:59:59`) : null;
        if (start > today) {
            return false;
        }
        if (end && end < today) {
            return false;
        }
        return true;
    };

    const handleAddToCart = async () => {
        if (!product) {
            return;
        }

        try {
            const discountId = selectedDiscount?.id ?? null;
            await addToCart(product, 1, discountId);
        } catch (error) {
            console.error("Error adding to cart:", error);
        }
    };

    const handleBuyNow = async () => {
        if (!isAuthenticated) {
            redirectToLogin(navigate, "/checkout");
            return;
        }
        await handleAddToCart();
        navigate("/checkout");
    };

    const handleDiscountChange = (event: ChangeEvent<HTMLInputElement>) => {
        if (!product) {
            return;
        }

        const selectedValue = event.target.value;
        setHasUserSelectedDiscount(true);
        if (selectedValue === "none") {
            setSelectedDiscount(null);
            return;
        }

        const discountId = Number(selectedValue);
        if (!Number.isFinite(discountId)) {
            setSelectedDiscount(null);
            return;
        }

        const discount = product.discounts?.find((d) => d.id === discountId) ?? null;
        setSelectedDiscount(discount);
    };

    if (isLoading) {
        return <p>Loading product...</p>;
    }

    if (loadError) {
        return <p>{loadError}</p>;
    }

    if (!product) {
        return <p>Product not found.</p>;
    }

    return (
        <Box sx={{ p: 4 }}>
            <Typography variant="h4" gutterBottom>
                {product.name}
            </Typography>

            {activeDiscounts.length > 0 && (
                <Box sx={{ mt: 2 }}>
                    <FormControl component="fieldset" fullWidth>
                        <FormLabel component="legend" sx={{ mb: 1, color: "text.secondary" }}>
                            Select an Offer:
                        </FormLabel>
                        <RadioGroup
                            name="discount-group"
                            value={selectedDiscount?.id !== undefined ? String(selectedDiscount.id) : "none"}
                            onChange={handleDiscountChange}
                        >
                            <FormControlLabel
                                value="none"
                                control={<Radio color="primary" />}
                                label="Original Price (No Discount)"
                                sx={{ mb: 1 }}
                            />

                            {activeDiscounts.map((discount) => (
                                <FormControlLabel
                                    key={discount.id ?? `${discount.description}-${discount.percentage}`}
                                    value={String(discount.id)}
                                    sx={{ width: "100%", ml: 0, mb: 1, alignItems: "flex-start" }}
                                    control={<Radio color="success" sx={{ mt: 1 }} />}
                                    label={
                                        <Box
                                            sx={{
                                                p: 1.5,
                                                width: "100%",
                                                border: "1px dashed #2e7d32",
                                                borderRadius: 1,
                                                bgcolor: "rgba(46, 125, 50, 0.05)",
                                                display: "flex",
                                                justifyContent: "space-between",
                                                alignItems: "center",
                                                ml: 1,
                                            }}
                                        >
                                            <Box>
                                                <Typography variant="body2" fontWeight="bold" color="success.main">
                                                    {discount.description || "Special Discount"}: {discount.percentage}% Off
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    Valid from {discount.startDate} {discount.endDate ? `to ${discount.endDate}` : ""}
                                                </Typography>
                                            </Box>
                                        </Box>
                                    }
                                />
                            ))}
                        </RadioGroup>
                    </FormControl>
                </Box>
            )}

            <Grid container spacing={2} sx={{ mt: 2 }}>
                <Grid size={{ xs: 12, md: 6 }}>
                    <Grid container spacing={1}>
                        {product.images?.map((img, index) => (
                            <Grid key={`${img}-${index}`} size={{ xs: 6 }}>
                                <Card sx={{ height: "100%" }}>
                                    <CardMedia component="img" image={img} alt={`Product image ${index + 1}`} sx={{ height: 200, objectFit: "cover" }} />
                                </Card>
                            </Grid>
                        ))}
                    </Grid>
                </Grid>

                <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body1" paragraph>
                        {product.description}
                    </Typography>
                    <Typography variant="subtitle1" color="text.secondary">
                        Category: {product.category}
                    </Typography>

                    <Box sx={{ mt: 1, mb: 2 }}>
                        {selectedDiscount ? (
                            <Box>
                                <Typography variant="h6" sx={{ textDecoration: "line-through", color: "text.secondary" }}>
                                    {currency.format(Number(product.price))}
                                </Typography>
                                <Typography variant="h4" color="success.main" fontWeight="bold">
                                    {currency.format(calculatePrice())}
                                </Typography>
                            </Box>
                        ) : (
                            <Typography variant="h5">Price: {currency.format(Number(product.price))}</Typography>
                        )}
                        {isUserDiscountActive() && (
                            <Chip
                                icon={<LocalOfferIcon fontSize="small" style={{ color: "inherit" }} />}
                                label={`User discount (-${user?.userDiscountPercentage}%)`}
                                size="small"
                                color="success"
                                variant="outlined"
                                sx={{ mt: 1 }}
                            />
                        )}
                    </Box>

                    <Stack spacing={2} sx={{ mt: 2, maxWidth: 400 }}>
                        <Stack direction="row" spacing={2}>
                            <Button variant="contained" color="primary" startIcon={<AddShoppingCartIcon />} onClick={handleAddToCart} sx={{ flexGrow: 1 }}>
                                Add to Cart
                            </Button>
                            <Button variant="contained" color="warning" startIcon={<FlashOnIcon />} onClick={handleBuyNow} sx={{ flexGrow: 1 }}>
                                Buy Now
                            </Button>
                        </Stack>
                    </Stack>
                </Grid>
            </Grid>

            <Box sx={{ mt: 5 }}>
                <Typography variant="h5" gutterBottom>
                    Customer Reviews
                </Typography>
                {reviews.length === 0 ? (
                    <Typography color="text.secondary">No reviews yet.</Typography>
                ) : (
                    reviews.map((review) => (
                        <Card key={review.id ?? `${review.user}-${review.comment}`} sx={{ mt: 2, p: 2 }}>
                            <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                                <Typography variant="subtitle2" sx={{ fontWeight: "bold" }}>{review.user}</Typography>
                                <Rating value={review.rating} readOnly size="small" />
                            </Stack>
                            <Typography variant="body2">{review.comment}</Typography>
                        </Card>
                    ))
                )}
            </Box>
        </Box>
    );
}

export default ProductDetails;
