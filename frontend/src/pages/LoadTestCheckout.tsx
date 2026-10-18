import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Container,
    Divider,
    Stack,
    TextField,
    Toolbar,
    Typography,
} from "@mui/material";
import { useNavigate, useSearchParams } from "react-router-dom";
import ApiService, { extractApiErrorMessage } from "../api/ApiService";
import { useCart } from "../global_component/cart-context";
import type { CartItem } from "../types/models";

type Outcome = "SUCCEEDED" | "FAILED" | "EXPIRED";

const currency = new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
});

const CARD_PLACEHOLDER = "4242 4242 4242 4242";

function formatCardNumber(value: string): string {
    return value
        .replace(/\D/g, "")
        .slice(0, 16)
        .replace(/(.{4})/g, "$1 ")
        .trim();
}

function getItemPrice(item: CartItem): number {
    return Number(item.finalPrice ?? item.price ?? 0);
}

export default function LoadTestCheckout() {
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const { cartItems } = useCart();

    const orderId = params.get("orderId") || "";
    const sessionId = params.get("sessionId") || "";
    const paymentId = params.get("paymentId") || "";

    const [status, setStatus] = useState("");
    const [paymentStatus, setPaymentStatus] = useState("");
    const [message, setMessage] = useState("");
    const [cardNumber, setCardNumber] = useState("");
    const [processing, setProcessing] = useState<Outcome | null>(null);
    const [error, setError] = useState("");

    const orderIdNumber = Number(orderId);

    const refreshStatus = useCallback(async () => {
        if (!Number.isFinite(orderIdNumber) || orderIdNumber <= 0) {
            return;
        }
        try {
            const data = await ApiService.getCheckoutStatus(orderIdNumber);
            setStatus(data.status || "");
            setPaymentStatus(data.paymentStatus || "");
            setMessage(data.message || "");
        } catch (err) {
            console.error("Failed to load checkout status", err);
        }
    }, [orderIdNumber]);

    useEffect(() => {
        refreshStatus().catch((error: unknown) => console.error("Status load failed", error));
    }, [refreshStatus]);

    const total = useMemo(
        () => cartItems.reduce((sum, item) => sum + getItemPrice(item) * (Number(item.quantity) || 0), 0),
        [cartItems]
    );

    const runOutcome = async (outcome: Outcome) => {
        setError("");
        setProcessing(outcome);

        try {
            const result = await ApiService.simulateLoadTestPayment(orderIdNumber, outcome);
            setStatus(result.status || "");
            setPaymentStatus(result.paymentStatus || "");
            setMessage(result.message || "");

            if (result.paymentStatus === "SUCCEEDED") {
                navigate("/checkout/success");
                return;
            }
            setError(result.message || "Payment was not approved.");
        } catch (e) {
            setError(extractApiErrorMessage(e, "Payment simulation failed."));
            await refreshStatus();
        } finally {
            setProcessing(null);
        }
    };

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (processing) {
            return;
        }
        runOutcome("SUCCEEDED").catch((error: unknown) => console.error("Payment failed", error));
    };

    const isPending = !paymentStatus || paymentStatus === "PENDING";

    const details = useMemo(
        () =>
            [
                ["Order", orderId],
                ["Session", sessionId],
                ["Payment", paymentId],
            ].filter(([, value]) => Boolean(value)),
        [orderId, sessionId, paymentId]
    );

    return (
        <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
            <Toolbar />
            <Container maxWidth="sm" sx={{ py: 4 }}>
                <Stack spacing={2}>
                    <Card>
                        <CardContent>
                            <Stack spacing={2}>
                                <Box>
                                    <Typography variant="h5">Complete your payment</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        Demo checkout. No Stripe or Razorpay API call is made — the event is signed
                                        locally and replayed through the same webhook path a real provider uses.
                                    </Typography>
                                </Box>

                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    {status && <Chip size="small" label={status} />}
                                    {paymentStatus && (
                                        <Chip
                                            size="small"
                                            color={paymentStatus === "SUCCEEDED" ? "success" : paymentStatus === "FAILED" ? "error" : "default"}
                                            label={paymentStatus}
                                        />
                                    )}
                                </Stack>

                                {message && <Typography variant="body2" color="text.secondary">{message}</Typography>}

                                {details.map(([label, value]) => (
                                    <Stack key={label} direction="row" justifyContent="space-between" gap={2}>
                                        <Typography variant="body2" color="text.secondary">{label}</Typography>
                                        <Typography variant="body2" sx={{ wordBreak: "break-all", textAlign: "right" }}>{value}</Typography>
                                    </Stack>
                                ))}

                                {cartItems.length > 0 && (
                                    <>
                                        <Divider />
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="body2" color="text.secondary">Items</Typography>
                                            <Typography variant="body2">{cartItems.length}</Typography>
                                        </Stack>
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="subtitle1">Total</Typography>
                                            <Typography variant="subtitle1" color="primary">{currency.format(total)}</Typography>
                                        </Stack>
                                    </>
                                )}
                            </Stack>
                        </CardContent>
                    </Card>

                    {error && <Alert severity="warning">{error}</Alert>}

                    <Card>
                        <CardContent>
                            <Stack spacing={2} component="form" onSubmit={handleSubmit}>
                                <TextField
                                    label="Card number"
                                    fullWidth
                                    value={cardNumber}
                                    onChange={(event) => setCardNumber(formatCardNumber(event.target.value))}
                                    placeholder={CARD_PLACEHOLDER}
                                    inputProps={{ inputMode: "numeric", autoComplete: "off" }}
                                    helperText="Simulated only. This value is never sent anywhere."
                                    disabled={processing !== null || !isPending}
                                />

                                <Button
                                    type="submit"
                                    variant="contained"
                                    size="large"
                                    disabled={processing !== null || !isPending}
                                    startIcon={processing === "SUCCEEDED" ? <CircularProgress size={18} color="inherit" /> : null}
                                >
                                    {processing === "SUCCEEDED" ? "Processing..." : `Pay ${currency.format(total)}`}
                                </Button>

                                <Button
                                    type="button"
                                    variant="outlined"
                                    color="error"
                                    disabled={processing !== null || !isPending}
                                    startIcon={processing === "FAILED" ? <CircularProgress size={18} color="inherit" /> : null}
                                    onClick={() => runOutcome("FAILED").catch((error: unknown) => console.error("Decline failed", error))}
                                >
                                    {processing === "FAILED" ? "Declining..." : "Simulate decline"}
                                </Button>

                                <Button
                                    type="button"
                                    variant="text"
                                    disabled={processing !== null}
                                    onClick={() => navigate("/checkout")}
                                >
                                    Cancel and return to cart
                                </Button>
                            </Stack>
                        </CardContent>
                    </Card>
                </Stack>
            </Container>
        </Box>
    );
}