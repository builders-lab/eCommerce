package com.ecommerce.platform.modules.users.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateManagerRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        String username
) {
}
