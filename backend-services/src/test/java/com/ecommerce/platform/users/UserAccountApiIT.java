package com.ecommerce.platform.users;

import com.ecommerce.platform.modules.users.api.UserAccountApi;
import com.ecommerce.platform.modules.users.api.UserRegistrationRequest;
import com.ecommerce.platform.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
class UserAccountApiIT extends IntegrationTestBase {

    @Autowired
    private UserAccountApi userAccountApi;

    @Test
    void register_user_returns_auth_identity() {
        ensureRole("ROLE_USER");

        var identity = userAccountApi.registerUser(new UserRegistrationRequest(
                "api-user@example.com",
                "secret123",
                "API User"
        ));

        assertThat(identity.email()).isEqualTo("api-user@example.com");
        assertThat(identity.displayName()).isEqualTo("API User");
        assertThat(identity.roles()).containsExactly("ROLE_USER");
        assertThat(userAccountApi.passwordMatches("secret123", identity)).isTrue();
    }

    @Test
    void register_user_rejects_duplicate_email() {
        ensureRole("ROLE_USER");
        createUser("duplicate-api@example.com", "secret123", "ROLE_USER");

        assertThatThrownBy(() -> userAccountApi.registerUser(new UserRegistrationRequest(
                "duplicate-api@example.com",
                "secret123",
                "Duplicate"
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void login_identity_exposes_inactive_state() {
        var user = createUser("inactive-api@example.com", "secret123", "ROLE_USER");
        user.setEnabled(false);
        user.setFlagged(true);
        userRepository.save(user);

        var identity = userAccountApi.loadByEmailForLogin("inactive-api@example.com");

        assertThat(identity.enabled()).isFalse();
        assertThat(identity.accountNonLocked()).isFalse();
    }

    @Test
    void create_manager_returns_user_admin_dto() {
        ensureRole("ROLE_MANAGER");

        var manager = userAccountApi.createManager(new UserRegistrationRequest(
                "manager-api@example.com",
                "secret123",
                "Manager API"
        ));

        assertThat(manager.email()).isEqualTo("manager-api@example.com");
        assertThat(manager.username()).isEqualTo("Manager API");
        assertThat(manager.roles()).containsExactly("ROLE_MANAGER");
    }
}
