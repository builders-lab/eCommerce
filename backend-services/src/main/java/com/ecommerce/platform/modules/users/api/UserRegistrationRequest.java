package com.ecommerce.platform.modules.users.api;

public record UserRegistrationRequest(
        String email,
        String password,
        String displayName
) {
}
