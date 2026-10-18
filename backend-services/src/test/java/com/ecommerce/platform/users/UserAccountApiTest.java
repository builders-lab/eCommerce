package com.ecommerce.platform.users;

import com.ecommerce.platform.modules.users.api.UserAccountApi;
import com.ecommerce.platform.modules.users.api.UserRegistrationRequest;
import com.ecommerce.platform.modules.users.repository.RoleRepository;
import com.ecommerce.platform.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountApiTest {

    @Test
    void register_user_fails_when_default_role_is_missing() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserAccountApi api = new UserAccountApi(userRepository, roleRepository, passwordEncoder);

        when(userRepository.existsByEmail("missing-role@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.registerUser(new UserRegistrationRequest(
                "missing-role@example.com",
                "secret123",
                "Missing Role"
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("500 INTERNAL_SERVER_ERROR");
    }
}
