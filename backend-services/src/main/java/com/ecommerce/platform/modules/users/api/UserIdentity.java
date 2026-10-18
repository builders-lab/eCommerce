package com.ecommerce.platform.modules.users.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserIdentity(
        Long id,
        String email,
        String displayName,
        String passwordHash,
        List<String> roles,
        boolean enabled,
        boolean accountNonLocked,
        BigDecimal userDiscountPercentage,
        LocalDate userDiscountStartDate,
        LocalDate userDiscountEndDate
) {
}
