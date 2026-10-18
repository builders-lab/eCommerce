package com.ecommerce.platform.modules.auth.dto;

public record AuthResponse(
        String token,
        UserDto user
) {}
