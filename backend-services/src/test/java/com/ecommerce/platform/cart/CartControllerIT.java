package com.ecommerce.platform.cart;

import com.ecommerce.platform.modules.cart.model.Cart;
import com.ecommerce.platform.modules.cart.model.CartItem;
import com.ecommerce.platform.modules.catalog.model.Product;
import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null")
public class CartControllerIT extends IntegrationTestBase {

    @Test
    void add_update_remove_cart_item_flow() throws Exception {
        User user = createUser("cart@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);
        Product product = createProduct("Phone", 499.99, "Electronics");

        String addBody = """
                {"productId": %d, "quantity": 2}
                """.formatted(product.getId());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(product.getId().intValue())))
                .andExpect(jsonPath("$.items[0].quantity", is(2)));

        String updateBody = """
                {"quantity": 3}
                """;

        mockMvc.perform(patch("/api/v1/cart/items/{id}", product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity", is(3)));

        mockMvc.perform(delete("/api/v1/cart/items/{id}", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void get_cart_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clear_cart() throws Exception {
        User user = createUser("clearcart@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);
        Product product = createProduct("Tablet", 199.99, "Electronics");

        Cart cart = cartRepository.save(new Cart(user.getId()));
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductId(product.getId());
        item.setQuantity(1);
        cartItemRepository.save(item);

        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
