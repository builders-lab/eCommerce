package com.ecommerce.platform.modules.auth.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserDto(
        Long id,
        String email,
        String username,
        List<String> roles,
        BigDecimal userDiscountPercentage,
        LocalDate userDiscountStartDate,
        LocalDate userDiscountEndDate
) {
}
