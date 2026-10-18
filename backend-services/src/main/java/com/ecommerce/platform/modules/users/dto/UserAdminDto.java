package com.ecommerce.platform.modules.users.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserAdminDto(
        Long id,
        String email,
        String username,
        List<String> roles,
        boolean flagged,
        BigDecimal userDiscountPercentage,
        LocalDate userDiscountStartDate,
        LocalDate userDiscountEndDate
) {
}
