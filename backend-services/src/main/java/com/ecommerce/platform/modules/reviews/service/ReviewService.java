package com.ecommerce.platform.modules.reviews.service;

import com.ecommerce.platform.config.CacheNames;
import com.ecommerce.platform.common.dto.PageResponse;
import com.ecommerce.platform.modules.catalog.api.CatalogApi;
import com.ecommerce.platform.modules.reviews.dto.request.AddReviewRequest;
import com.ecommerce.platform.modules.reviews.dto.response.ReviewResponse;
import com.ecommerce.platform.modules.reviews.model.Review;
import com.ecommerce.platform.modules.reviews.repository.ReviewRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CatalogApi catalogApi;

    public ReviewService(ReviewRepository reviewRepository, CatalogApi catalogApi) {
        this.reviewRepository = reviewRepository;
        this.catalogApi = catalogApi;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_REVIEWS, key = "{#productId, #page, #size}")
    public PageResponse<ReviewResponse> getReviewsByProductId(Long productId, int page, int size) {
        Page<ReviewResponse> reviews = reviewRepository.findByProductId(
                        productId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                )
                .map(this::toResponse);
        return PageResponse.from(reviews, item -> item);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCT_REVIEWS, allEntries = true)
    public ReviewResponse addReview(Long productId, AddReviewRequest request, String displayName) {
        catalogApi.requireProductExists(productId);

        var review = new Review();
        review.setProductId(productId);
        review.setUser(displayName);
        review.setRating(request.rating());
        review.setComment(request.comment());

        return toResponse(reviewRepository.save(review));
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getUser(),
                review.getComment(),
                review.getRating(),
                review.getProductId(),
                review.getCreatedAt()
        );
    }
}
