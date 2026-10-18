import { lazy, Suspense, useState } from "react";
import type { PropsWithChildren, ReactElement } from "react";
import { BrowserRouter as Router, Navigate, Route, Routes, useLocation } from "react-router-dom";
import Header from "./components/Header";
import AdminRoute from "./global_component/AdminRoute";
import { AuthProvider } from "./global_component/AuthContext";
import { CartProvider } from "./global_component/CartContext";
import ManagerRoute from "./global_component/ManagerRoute";
import { useAuth } from "./global_component/auth-context";
import { buildLoginRedirectPath } from "./global_component/authUtils";

const MainPage = lazy(() => import("./pages/MainPage"));
const SignUp = lazy(() => import("./pages/SignUp"));
const ProductDetails = lazy(() => import("./pages/ProductDetails"));
const Buy = lazy(() => import("./pages/Buy"));
const CheckoutSuccess = lazy(() => import("./pages/CheckoutSuccess"));
const LoadTestCheckout = lazy(() => import("./pages/LoadTestCheckout"));
const RazorpayCheckout = lazy(() => import("./pages/RazorpayCheckout"));
const Login = lazy(() => import("./pages/Login"));
const AddProduct = lazy(() => import("./pages/admin/AddProduct"));
const AddManager = lazy(() => import("./pages/admin/AddManager"));
const ManageUsers = lazy(() => import("./pages/admin/ManageUsers"));
const AdminDashboard = lazy(() => import("./pages/admin/AdminDashboard"));
const ManagerDashboard = lazy(() => import("./pages/admin/ManagerDashboard"));

function ProtectedRoute({ children }: PropsWithChildren): ReactElement {
    const { isAuthenticated } = useAuth();
    const location = useLocation();

    if (!isAuthenticated) {
        const redirectTarget = `${location.pathname}${location.search}`;
        return <Navigate to={buildLoginRedirectPath(redirectTarget)} replace />;
    }

    return <>{children}</>;
}

export default function App() {
    const [drawerOpen, setDrawerOpen] = useState(false);

    const toggleDrawer = (nextState?: boolean) => {
        setDrawerOpen((prev) => (typeof nextState === "boolean" ? nextState : !prev));
    };

    return (
        <AuthProvider>
            <CartProvider>
                <Router>
                    <Header onMenuClick={toggleDrawer} />
                    <Suspense fallback={<div role="status" aria-live="polite">Loading page...</div>}>
                        <Routes>
                            <Route path="/" element={<MainPage drawerOpen={drawerOpen} toggleDrawer={toggleDrawer} />} />
                            <Route path="/product/:id" element={<ProductDetails />} />
                            <Route path="/login" element={<Login />} />
                            <Route path="/signup" element={<SignUp />} />

                            <Route element={<AdminRoute />}>
                                <Route path="/admin/dashboard" element={<AdminDashboard />} />
                                <Route path="/admin/add-product" element={<AddProduct />} />
                                <Route path="/admin/add-manager" element={<AddManager />} />
                                <Route path="/admin/users" element={<ManageUsers />} />
                            </Route>

                            <Route element={<ManagerRoute />}>
                                <Route path="/manager/dashboard" element={<ManagerDashboard />} />
                                <Route path="/manager/add-product" element={<AddProduct />} />
                                <Route path="/manager/users" element={<ManageUsers />} />
                            </Route>

                            <Route
                                path="/checkout"
                                element={
                                    <ProtectedRoute>
                                        <Buy />
                                    </ProtectedRoute>
                                }
                            />
                            <Route path="/checkout/loadtest" element={<LoadTestCheckout />} />
                            <Route path="/checkout/razorpay" element={<RazorpayCheckout />} />
                            <Route path="/checkout/success" element={<CheckoutSuccess />} />
                            <Route path="/checkout/cancel" element={<Navigate to="/checkout" replace />} />
                            <Route path="*" element={<Navigate to="/" replace />} />
                        </Routes>
                    </Suspense>
                </Router>
            </CartProvider>
        </AuthProvider>
    );
}
