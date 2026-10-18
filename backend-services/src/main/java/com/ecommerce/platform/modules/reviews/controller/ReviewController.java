package com.ecommerce.platform.modules.reviews.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.ecommerce.platform.common.dto.PageResponse;
import com.ecommerce.platform.modules.reviews.dto.request.AddReviewRequest;
import com.ecommerce.platform.modules.reviews.dto.response.ReviewResponse;
import com.ecommerce.platform.modules.reviews.service.ReviewService;
import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public PageResponse<ReviewResponse> getReviews(@PathVariable("productId") Long productId,
                                                   @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.getReviewsByProductId(productId, page, size);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> addReview(@PathVariable("productId") Long productId,
                                                    @Valid @RequestBody AddReviewRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .body(reviewService.addReview(productId, request, user.getDisplayName()));
    }
}
