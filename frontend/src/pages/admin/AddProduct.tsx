import { useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { Box, TextField, Button, Typography, Paper, Grid, MenuItem, Alert, Stack, CircularProgress } from "@mui/material";
import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import { addProduct, uploadImage, extractApiErrorMessage } from "../../api/ApiService";
import type { Discount, ProductPayload } from "../../types/models";
import { useNavigate } from "react-router-dom";

const CATEGORIES = ["Electronics", "Fashion", "Home", "Beauty", "Books", "Toys"];

interface AddProductForm {
    name: string;
    description: string;
    price: string;
    category: string;
    imageUrl: string;
    discountPercentage: string;
    discountStartDate: string;
    discountEndDate: string;
}

function AddProduct() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState(false);

    const [formData, setFormData] = useState<AddProductForm>({
        name: "",
        description: "",
        price: "",
        category: "",
        imageUrl: "",
        discountPercentage: "",
        discountStartDate: "",
        discountEndDate: "",
    });

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) {
            return;
        }

        setUploading(true);
        setError("");

        try {
            const url = await uploadImage(file);
            setFormData((prev) => ({ ...prev, imageUrl: url }));
        } catch (err) {
            console.error(err);
            setError(extractApiErrorMessage(err, "Failed to upload image."));
        } finally {
            setUploading(false);
        }
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        setSuccess(false);

        try {
            const price = Number(formData.price);
            if (!Number.isFinite(price) || price <= 0) {
                throw new Error("Price must be a valid positive number.");
            }

            const hasDiscount = formData.discountPercentage !== "" && formData.discountStartDate !== "";
            const discounts: Discount[] = hasDiscount
                ? [{
                    description: "Admin Discount",
                    percentage: Number(formData.discountPercentage),
                    startDate: formData.discountStartDate,
                    endDate: formData.discountEndDate || null,
                }]
                : [];

            const payload: ProductPayload = {
                name: formData.name,
                description: formData.description,
                category: formData.category,
                price,
                images: [formData.imageUrl],
                discounts,
            };

            await addProduct(payload);
            setSuccess(true);
            setTimeout(() => navigate("/"), 2000);
        } catch (err) {
            console.error(err);
            setError(extractApiErrorMessage(err, "Failed to add product."));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <Paper elevation={3} sx={{ p: 4, width: "100%", maxWidth: 800 }}>
                <Typography variant="h4" gutterBottom sx={{ mb: 4, fontWeight: "bold" }}>
                    Add New Product
                </Typography>

                {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
                {success && <Alert severity="success" sx={{ mb: 3 }}>Product added successfully!</Alert>}

                <form onSubmit={handleSubmit}>
                    <Grid container spacing={3}>
                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="Product Name" name="name" value={formData.name} onChange={handleChange} required />
                        </Grid>

                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth select label="Category" name="category" value={formData.category} onChange={handleChange} required>
                                {CATEGORIES.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                        </Grid>

                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="Price" name="price" type="number" value={formData.price} onChange={handleChange} required />
                        </Grid>

                        <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack direction="row" spacing={2} alignItems="center">
                                <Button variant="outlined" component="label" startIcon={<CloudUploadIcon />} disabled={uploading}>
                                    {uploading ? "Uploading..." : "Upload Image"}
                                    <input type="file" hidden accept="image/*" onChange={handleFileChange} />
                                </Button>
                                {uploading && <CircularProgress size={24} />}
                            </Stack>

                            <TextField
                                fullWidth
                                margin="dense"
                                label="Image URL (Auto-filled)"
                                name="imageUrl"
                                value={formData.imageUrl}
                                InputProps={{ readOnly: true }}
                                helperText="Upload an image to generate this URL automatically"
                            />
                        </Grid>

                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="Discount Percentage" name="discountPercentage" type="number" value={formData.discountPercentage} onChange={handleChange} />
                        </Grid>

                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="Discount Start Date" name="discountStartDate" type="date" value={formData.discountStartDate} onChange={handleChange} InputLabelProps={{ shrink: true }} />
                            <TextField fullWidth label="Discount End Date" name="discountEndDate" type="date" value={formData.discountEndDate} onChange={handleChange} InputLabelProps={{ shrink: true }} />
                        </Grid>

                        <Grid size={{ xs: 12 }}>
                            <TextField fullWidth multiline rows={4} label="Description" name="description" value={formData.description} onChange={handleChange} required />
                        </Grid>

                        <Grid size={{ xs: 12 }}>
                            <Stack direction="row" spacing={2} justifyContent="flex-end">
                                <Button variant="outlined" onClick={() => navigate("/")} disabled={loading}>Cancel</Button>
                                <Button type="submit" variant="contained" size="large" disabled={loading || uploading || !formData.imageUrl}>
                                    {loading ? "Publishing..." : "Publish Product"}
                                </Button>
                            </Stack>
                        </Grid>
                    </Grid>
                </form>
            </Paper>
        </Box>
    );
}

export default AddProduct;
