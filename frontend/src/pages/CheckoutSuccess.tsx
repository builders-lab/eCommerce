import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { Box, CircularProgress, Toolbar } from "@mui/material";
import ApiService from "../api/ApiService";
import { useCart } from "../global_component/cart-context";

const PENDING_CHECKOUT_ORDER_KEY = "pendingCheckoutOrderId";
const MAX_STATUS_POLLS = 20;
const STATUS_POLL_INTERVAL_MS = 1000;

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
}

export default function CheckoutSuccess() {
    const { reload } = useCart();
    const [done, setDone] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function refreshAfterPayment() {
            const orderId = sessionStorage.getItem(PENDING_CHECKOUT_ORDER_KEY);

            try {
                if (orderId) {
                    for (let attempt = 0; attempt < MAX_STATUS_POLLS; attempt += 1) {
                        const status = await ApiService.getCheckoutStatus(orderId);
                        if (status.paymentStatus === "SUCCEEDED") {
                            break;
                        }
                        if (status.paymentStatus === "FAILED" || status.paymentStatus === "EXPIRED") {
                            break;
                        }
                        await sleep(STATUS_POLL_INTERVAL_MS);
                    }
                }
            } finally {
                sessionStorage.removeItem(PENDING_CHECKOUT_ORDER_KEY);
                await reload();
            }
        }

        refreshAfterPayment()
            .catch((error: unknown) => console.error("Failed to refresh cart after checkout", error))
            .finally(() => {
                if (!cancelled) {
                    setDone(true);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [reload]);

    if (done) {
        return <Navigate to="/" replace />;
    }

    return (
        <Box sx={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Toolbar />
            <CircularProgress />
        </Box>
    );
}
