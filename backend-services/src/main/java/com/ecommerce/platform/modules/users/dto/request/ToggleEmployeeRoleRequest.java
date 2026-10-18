package com.ecommerce.platform.modules.users.dto.request;

import jakarta.validation.constraints.NotNull;

public record ToggleEmployeeRoleRequest(
        @NotNull Boolean enabled
) {
}
