import { Box, Button, Card, CardContent, Container, Stack, Toolbar, Typography } from "@mui/material";
import { useNavigate, useSearchParams } from "react-router-dom";

/**
 * Placeholder for the URL that RazorpayPaymentService returns when
 * APP_PAYMENT_GATEWAY=razorpay.
 *
 * The backend already creates the Razorpay order and signs webhooks, but the
 * browser-side Checkout.js integration has not been implemented in this build.
 * Without this route the provider URL falls through to the catch-all redirect and
 * silently lands on the store home page, which looks like a bug rather than an
 * unfinished integration.
 */
export default function RazorpayCheckout() {
    const navigate = useNavigate();
    const [params] = useSearchParams();

    const details = [
        ["Order", params.get("orderId") || ""],
        ["Razorpay order", params.get("orderRef") || ""],
        ["Key ID", params.get("keyId") || ""],
    ].filter(([, value]) => Boolean(value));

    return (
        <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
            <Toolbar />
            <Container maxWidth="sm" sx={{ py: 4 }}>
                <Card>
                    <CardContent>
                        <Stack spacing={2}>
                            <Typography variant="h5">Razorpay checkout</Typography>
                            <Typography variant="body2" color="text.secondary">
                                The backend order and webhook verification are implemented, but the browser-side
                                Razorpay Checkout.js integration has not been wired up in this build. Run with
                                APP_PAYMENT_GATEWAY=loadtest for the fully working local demo checkout.
                            </Typography>

                            {details.map(([label, value]) => (
                                <Stack key={label} direction="row" justifyContent="space-between" gap={2}>
                                    <Typography variant="body2" color="text.secondary">{label}</Typography>
                                    <Typography variant="body2" sx={{ wordBreak: "break-all", textAlign: "right" }}>{value}</Typography>
                                </Stack>
                            ))}

                            <Button variant="contained" onClick={() => navigate("/checkout")}>
                                Return to Cart
                            </Button>
                        </Stack>
                    </CardContent>
                </Card>
            </Container>
        </Box>
    );
}