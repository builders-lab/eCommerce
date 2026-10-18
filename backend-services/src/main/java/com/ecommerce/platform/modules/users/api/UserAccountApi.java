package com.ecommerce.platform.modules.users.api;

import com.ecommerce.platform.modules.users.dto.UserAdminDto;
import com.ecommerce.platform.modules.users.model.Role;
import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.modules.users.repository.RoleRepository;
import com.ecommerce.platform.modules.users.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class UserAccountApi {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountApi(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserIdentity registerUser(UserRegistrationRequest request) {
        validateRegistration(request);
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(resolveDisplayName(request, request.email().split("@")[0]));
        user.setRoles(Set.of(getRole("ROLE_USER")));

        return toIdentity(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserIdentity loadByEmailForLogin(String email) {
        return userRepository.findByEmailWithRoles(email)
                .map(this::toIdentity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    @Transactional
    public UserAdminDto createManager(UserRegistrationRequest request) {
        validateRegistration(request);
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(resolveDisplayName(request, "Manager"));
        user.setRoles(Set.of(getRole("ROLE_MANAGER")));

        return toAdminDto(userRepository.save(user));
    }

    public boolean passwordMatches(String rawPassword, UserIdentity identity) {
        return passwordEncoder.matches(rawPassword, identity.passwordHash());
    }

    private void validateRegistration(UserRegistrationRequest request) {
        if (request.email() == null || request.email().isBlank() ||
                request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
    }

    private Role getRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Role '" + roleName + "' is not found"));
    }

    private String resolveDisplayName(UserRegistrationRequest request, String fallback) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            return request.displayName();
        }
        return fallback;
    }

    private UserIdentity toIdentity(User user) {
        return new UserIdentity(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPassword(),
                user.getRoles().stream().map(Role::getName).toList(),
                user.isEnabled(),
                user.isAccountNonLocked(),
                user.getUserDiscountPercentage(),
                user.getUserDiscountStartDate(),
                user.getUserDiscountEndDate()
        );
    }

    private UserAdminDto toAdminDto(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        return new UserAdminDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                user.isFlagged(),
                user.getUserDiscountPercentage(),
                user.getUserDiscountStartDate(),
                user.getUserDiscountEndDate()
        );
    }
}
