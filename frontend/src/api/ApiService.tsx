import axios, { type AxiosError } from "axios";
import type {
    AuthResponse,
    CartItem,
    CartResponse,
    Discount,
    LoginRequest,
    PagedResponse,
    Product,
    ProductPayload,
    Review,
    ReviewPayload,
    SignupRequest,
    User,
} from "../types/models";

const API_PREFIX = import.meta.env.VITE_API_PREFIX || "/api/v1";
const AUTH_PREFIX = import.meta.env.VITE_AUTH_PREFIX || "/auth";

const api = axios.create({
    baseURL: API_PREFIX,
    withCredentials: true,
    headers: {
        "Content-Type": "application/json",
    },
});

const authApi = axios.create({
    baseURL: AUTH_PREFIX,
    withCredentials: true,
    headers: {
        "Content-Type": "application/json",
    },
});

interface ApiErrorResponse {
    message?: string;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

function asError(error: unknown): AxiosError<ApiErrorResponse> | null {
    if (axios.isAxiosError(error)) {
        return error;
    }
    return null;
}

api.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
        const axiosError = asError(error);
        if (axiosError?.response?.status === 401) {
            unauthorizedHandler?.();
        }
        return Promise.reject(error);
    },
);

function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
    unauthorizedHandler = handler;
}

export async function fetchCategories(): Promise<string[]> {
    try {
        const { data } = await api.get<unknown>("/products/categories");
        if (!Array.isArray(data)) {
            return ["All"];
        }

        return ["All", ...data.filter((entry): entry is string => typeof entry === "string")];
    } catch (err) {
        console.error("Error fetching categories:", err);
        throw err;
    }
}

export async function fetchProducts(category: string): Promise<Product[]> {
    try {
        const params: { page: number; size: number; category?: string } = { page: 0, size: 100 };
        if (category && category !== "All") {
            params.category = category;
        }

        const { data } = await api.get<PagedResponse<Product> | Product[]>("/products", { params });
        if (Array.isArray(data)) {
            return data;
        }

        return Array.isArray(data?.items) ? data.items : [];
    } catch (err) {
        console.error("Error fetching products:", err);
        throw err;
    }
}

export async function fetchProduct(id: number | string | undefined): Promise<Product | null> {
    if (id === undefined || id === null || id === "") {
        return null;
    }

    try {
        const { data } = await api.get<Product>(`/products/${id}`);
        return data;
    } catch (err) {
        console.error("Error fetching product:", err);
        return null;
    }
}

export async function fetchReviews(productId: number | string | undefined): Promise<Review[]> {
    if (productId === undefined || productId === null || productId === "") {
        return [];
    }

    try {
        const { data } = await api.get<PagedResponse<Review> | Review[]>(`/products/${productId}/reviews`);
        if (Array.isArray(data)) {
            return data;
        }
        return Array.isArray(data?.items) ? data.items : [];
    } catch (err) {
        console.error("Error fetching reviews:", err);
        return [];
    }
}

async function login({ email, password }: LoginRequest): Promise<AuthResponse> {
    const { data } = await authApi.post<AuthResponse>("/login", { email, password });
    return data;
}

async function signup({ email, password, username }: SignupRequest): Promise<AuthResponse> {
    const { data } = await authApi.post<AuthResponse>("/signup", { email, password, username });
    return data;
}

function setAuthToken(token: string | null): void {
    if (token) {
        api.defaults.headers.common.Authorization = `Bearer ${token}`;
        authApi.defaults.headers.common.Authorization = `Bearer ${token}`;
    } else {
        delete api.defaults.headers.common.Authorization;
        delete authApi.defaults.headers.common.Authorization;
    }
}

function clearAuthToken(): void {
    delete api.defaults.headers.common.Authorization;
    delete authApi.defaults.headers.common.Authorization;
}

async function getCart(): Promise<CartItem[]> {
    const { data } = await api.get<CartResponse>("/cart");
    return data.items ?? [];
}

export async function addOrUpdateCartItem(
    productId: number,
    quantity: number,
    discountId: number | null = null,
): Promise<CartResponse> {
    const { data } = await api.post<CartResponse>("/cart/items", { productId, quantity, discountId });
    return data;
}

async function updateCartItem(productId: number, quantity: number): Promise<CartResponse> {
    const { data } = await api.patch<CartResponse>(`/cart/items/${productId}`, { quantity });
    return data;
}

export async function updateCartItemDiscount(productId: number, discountId: number | null): Promise<CartResponse> {
    const { data } = await api.patch<CartResponse>(`/cart/items/${productId}/discount`, { discountId });
    return data;
}

async function removeCartItem(productId: number): Promise<CartResponse> {
    const { data } = await api.delete<CartResponse>(`/cart/items/${productId}`);
    return data;
}

async function clearCart(): Promise<CartResponse> {
    const { data } = await api.delete<CartResponse>("/cart");
    return data;
}

interface CheckoutResponse {
    orderId: number;
    status: string;
    checkoutUrl?: string;
    expiresAt?: string | null;
}

interface CheckoutStatusResponse {
    orderId: number;
    status: string;
    paymentStatus: string;
    message: string;
}

async function startCheckout(): Promise<CheckoutResponse> {
    const { data } = await api.post<CheckoutResponse>("/checkout", {});
    return data;
}

async function getCheckoutStatus(orderId: number | string): Promise<CheckoutStatusResponse> {
    const { data } = await api.get<CheckoutStatusResponse>(`/checkout/${orderId}`);
    return data;
}

/**
 * Settles an order created by the local demo gateway (APP_PAYMENT_GATEWAY=loadtest).
 * Only available while that gateway is configured.
 */
async function simulateLoadTestPayment(
    orderId: number | string,
    outcome: "SUCCEEDED" | "FAILED" | "EXPIRED"
): Promise<CheckoutStatusResponse> {
    const { data } = await api.post<CheckoutStatusResponse>(`/checkout/${orderId}/simulate-payment`, { outcome });
    return data;
}

export async function addProduct(productData: ProductPayload): Promise<Product> {
    const { data } = await api.post<Product>("/products", productData);
    return data;
}

export async function updateProduct(id: number, productData: ProductPayload): Promise<Product> {
    const { data } = await api.put<Product>(`/products/${id}`, productData);
    return data;
}

export async function deleteProduct(id: number): Promise<void> {
    await api.delete(`/products/${id}`);
}

export async function addReview(productId: number, reviewData: ReviewPayload): Promise<Review> {
    const { data } = await api.post<Review>(`/products/${productId}/reviews`, reviewData);
    return data;
}

export async function getAllUsers(): Promise<User[]> {
    const { data } = await api.get<PagedResponse<User> | User[]>("/users", { params: { page: 0, size: 100 } });
    if (Array.isArray(data)) {
        return data;
    }

    return data?.items ?? [];
}

export async function flagUser(userId: number): Promise<User> {
    const { data } = await api.patch<User>(`/users/${userId}/flag`);
    return data;
}

export async function unflagUser(userId: number): Promise<User> {
    const { data } = await api.patch<User>(`/users/${userId}/unflag`);
    return data;
}

export async function deleteUser(userId: number): Promise<void> {
    await api.delete(`/users/${userId}`);
}

export async function registerManager(managerData: SignupRequest): Promise<User> {
    const { data } = await api.post<User>("/users/managers", managerData);
    return data;
}

export async function updateUserDiscount(
    userId: number,
    percentage: number,
    startDate: string | null = null,
    endDate: string | null = null,
): Promise<User> {
    const { data } = await api.patch<User>(`/users/${userId}/discount`, { percentage, startDate, endDate });
    return data;
}

export async function setEmployeeRole(userId: number, enabled: boolean): Promise<User> {
    const { data } = await api.patch<User>(`/users/${userId}/employee`, { enabled });
    return data;
}

export async function uploadImage(file: File): Promise<string> {
    const formData = new FormData();
    formData.append("file", file);

    const { data } = await api.post<{ url?: string }>("/images/upload", formData, {
        headers: {
            "Content-Type": "multipart/form-data",
        },
    });

    if (!data?.url) {
        throw new Error("Upload response did not include image URL");
    }

    return data.url;
}

export function extractApiErrorMessage(error: unknown, fallback: string): string {
    const axiosError = asError(error);
    return axiosError?.response?.data?.message || fallback;
}

export type { Discount };

export default {
    api,
    authApi,
    setAuthToken,
    clearAuthToken,
    setUnauthorizedHandler,
    login,
    signup,
    getCart,
    addOrUpdateCartItem,
    updateCartItem,
    updateCartItemDiscount,
    removeCartItem,
    clearCart,
    startCheckout,
    getCheckoutStatus,
    simulateLoadTestPayment,
    fetchCategories,
    fetchProducts,
    fetchProduct,
    fetchReviews,
    addProduct,
    updateProduct,
    deleteProduct,
    addReview,
    getAllUsers,
    flagUser,
    unflagUser,
    deleteUser,
    registerManager,
    updateUserDiscount,
    setEmployeeRole,
    uploadImage,
    extractApiErrorMessage,
};
