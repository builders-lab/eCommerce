import { createContext, useContext } from "react";
import type { CartItem, Product } from "../types/models";

export interface CartContextValue {
    cartItems: CartItem[];
    cartCount: number;
    loading: boolean;
    addToCart: (product: Product, qty?: number, discountId?: number | null) => Promise<void>;
    updateQuantity: (productId: number, quantity: number) => Promise<void>;
    removeFromCart: (productId: number) => Promise<void>;
    clear: () => Promise<void>;
    reload: () => Promise<void>;
}

export const CartContext = createContext<CartContextValue | undefined>(undefined);

export function useCart(): CartContextValue {
    const context = useContext(CartContext);
    if (!context) {
        throw new Error("useCart must be used within CartProvider");
    }
    return context;
}
