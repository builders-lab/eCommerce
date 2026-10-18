package com.ecommerce.platform.reviews;

import com.ecommerce.platform.modules.catalog.model.Product;
import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null")
public class ReviewControllerIT extends IntegrationTestBase {

    @Test
    void get_reviews_empty() throws Exception {
        Product product = createProduct("Book", 15.99, "Books");

        mockMvc.perform(get("/api/v1/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void add_review_requires_authentication() throws Exception {
        Product product = createProduct("Notebook", 7.99, "Books");
        String body = """
                {"rating": 5, "comment": "Great!"}
                """;

        mockMvc.perform(post("/api/v1/products/{id}/reviews", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_review_success() throws Exception {
        User user = createUser("reviewer@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);
        Product product = createProduct("Pen", 1.99, "Stationery");

        String body = """
                {"rating": 4, "comment": "Nice"}
                """;

        mockMvc.perform(post("/api/v1/products/{id}/reviews", product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user", is(user.getDisplayName())))
                .andExpect(jsonPath("$.comment", is("Nice")));

        mockMvc.perform(get("/api/v1/products/{id}/reviews", product.getId())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].comment", is("Nice")));
    }

    @Test
    void reviews_pagination_rejects_invalid_size() throws Exception {
        Product product = createProduct("Ruler", 2.99, "Stationery");

        mockMvc.perform(get("/api/v1/products/{id}/reviews", product.getId())
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviews_pagination_rejects_negative_page() throws Exception {
        Product product = createProduct("Scale", 3.49, "Stationery");

        mockMvc.perform(get("/api/v1/products/{id}/reviews", product.getId())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
