package com.ecommerce.platform.modules.reviews.dto.response;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        String user,
        String comment,
        int rating,
        Long productId,
        Instant createdAt
) {
}
