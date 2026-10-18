package com.ecommerce.platform.catalog;

import tools.jackson.databind.ObjectMapper;
import com.ecommerce.platform.modules.catalog.model.Product;
import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
public class ProductControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void get_products_and_product_by_id() throws Exception {
        Product product = createProduct("Laptop", 999.99, "Electronics");

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Laptop")));

        mockMvc.perform(get("/api/v1/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Electronics")));

        mockMvc.perform(get("/api/v1/products").param("category", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void get_products_rejects_size_above_limit() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_product_requires_admin_or_manager() throws Exception {
        User user = createUser("user@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Mouse",
                "description", "Wireless mouse",
                "category", "Electronics",
                "price", 29.99,
                "images", java.util.List.of("http://example.com/mouse.png")
        ));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_as_admin() throws Exception {
        User admin = createUser("admin@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Keyboard",
                "description", "Mechanical keyboard",
                "category", "Electronics",
                "price", 79.99,
                "images", java.util.List.of("http://example.com/keyboard.png")
        ));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Keyboard")));
    }

    @Test
    void update_and_delete_product_as_admin() throws Exception {
        User admin = createUser("admin5@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);
        Product product = createProduct("OldName", 10.0, "Misc");

        String updateBody = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "NewName",
                "description", "Updated",
                "category", "Misc",
                "price", 12.5,
                "images", java.util.List.of("http://example.com/new.png")
        ));

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewName")));

        mockMvc.perform(delete("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
