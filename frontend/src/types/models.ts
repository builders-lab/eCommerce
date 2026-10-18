export interface Discount {
    id?: number;
    description?: string;
    percentage: number;
    startDate?: string | null;
    endDate?: string | null;
}

export interface Product {
    id: number;
    name: string;
    description: string;
    category: string;
    price: number;
    images: string[];
    discounts?: Discount[];
}

export interface Review {
    id?: number;
    user: string;
    rating: number;
    comment: string;
}

export interface User {
    id: number;
    email: string;
    username?: string | null;
    roles: string[];
    flagged?: boolean;
    enabled?: boolean;
    userDiscountPercentage?: number;
    userDiscountStartDate?: string | null;
    userDiscountEndDate?: string | null;
}

export interface AuthResponse {
    token: string;
    user: User;
}

export interface CartItem {
    id: number;
    productId?: number;
    title: string;
    imageUrl?: string;
    quantity: number;
    price: number;
    finalPrice?: number;
    productDiscount?: Discount | null;
    totalDiscountPercentage?: number;
    userDiscountPercentage?: number;
    employeeDiscountPercentage?: number;
}

export interface CartResponse {
    items: CartItem[];
}

export interface PagedResponse<T> {
    items: T[];
    page?: number;
    size?: number;
    totalItems?: number;
    totalPages?: number;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface SignupRequest {
    email: string;
    password: string;
    username: string;
}

export interface ProductPayload {
    name: string;
    description: string;
    category: string;
    price: number;
    images: string[];
    discounts?: Discount[];
}

export interface ReviewPayload {
    rating: number;
    comment: string;
}
