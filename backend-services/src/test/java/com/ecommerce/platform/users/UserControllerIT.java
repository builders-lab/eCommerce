package com.ecommerce.platform.users;

import tools.jackson.databind.ObjectMapper;
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
public class UserControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void list_users_requires_admin_or_manager() throws Exception {
        User user = createUser("basic@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_list_users() throws Exception {
        User admin = createUser("admin2@example.com", "secret123", "ROLE_ADMIN");
        createUser("target@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(admin);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(2)));
    }

    @Test
    void admin_list_users_rejects_size_above_limit() throws Exception {
        User admin = createUser("admin-limit@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manager_can_flag_regular_user_but_not_admin() throws Exception {
        User manager = createUser("manager@example.com", "secret123", "ROLE_MANAGER");
        User target = createUser("target2@example.com", "secret123", "ROLE_USER");
        User admin = createUser("admin3@example.com", "secret123", "ROLE_ADMIN");
        String managerToken = tokenFor(manager);

        mockMvc.perform(patch("/api/v1/users/{id}/flag", target.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagged", is(true)));

        mockMvc.perform(patch("/api/v1/users/{id}/flag", admin.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_update_user_discount() throws Exception {
        User admin = createUser("admin4@example.com", "secret123", "ROLE_ADMIN");
        User target = createUser("discount@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(admin);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "percentage", 10,
                "startDate", "2025-01-01",
                "endDate", "2025-12-31"
        ));

        mockMvc.perform(patch("/api/v1/users/{id}/discount", target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDiscountPercentage", is(10)));
    }

    @Test
    void manager_cannot_set_discount_for_self() throws Exception {
        User manager = createUser("manager2@example.com", "secret123", "ROLE_MANAGER");
        String token = tokenFor(manager);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "percentage", 5,
                "startDate", "2025-01-01"
        ));

        mockMvc.perform(patch("/api/v1/users/{id}/discount", manager.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_create_manager() throws Exception {
        User admin = createUser("admin5@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "manager-new@example.com",
                "password", "secret123",
                "username", "manager2"
        ));

        mockMvc.perform(post("/api/v1/users/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("manager-new@example.com")))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_MANAGER")));
    }

    @Test
    void admin_can_set_employee_role_and_delete_user() throws Exception {
        User admin = createUser("admin6@example.com", "secret123", "ROLE_ADMIN");
        User target = createUser("employee@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(admin);

        String body = objectMapper.writeValueAsString(java.util.Map.of("enabled", true));

        mockMvc.perform(patch("/api/v1/users/{id}/employee", target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_EMPLOYEE")));

        mockMvc.perform(delete("/api/v1/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
