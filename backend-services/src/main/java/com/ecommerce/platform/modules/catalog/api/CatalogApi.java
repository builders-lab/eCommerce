package com.ecommerce.platform.modules.catalog.api;

import com.ecommerce.platform.modules.catalog.model.Discount;
import com.ecommerce.platform.modules.catalog.model.Product;
import com.ecommerce.platform.modules.catalog.repository.ProductImageRow;
import com.ecommerce.platform.modules.catalog.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CatalogApi {

    private final ProductRepository productRepository;

    public CatalogApi(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public CatalogCartProduct getProductForCart(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return toCartProduct(product);
    }

    @Transactional(readOnly = true)
    public Map<Long, CatalogCartProduct> getProductsForCart(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> requested = productIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return productRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        this::toCartProduct,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public void requireProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    @Transactional(readOnly = true)
    public CatalogDiscount getBestActiveDiscount(Long productId) {
        Product product = productRepository.findForCartMutationById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return product.getDiscounts().stream()
                .filter(this::isDiscountActive)
                .max(Comparator.comparing(Discount::getPercentage))
                .map(this::toDiscount)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CatalogDiscount getActiveDiscount(Long productId, Long discountId) {
        Product product = productRepository.findForCartMutationById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return product.getDiscounts().stream()
                .filter(discount -> discount.getId().equals(discountId))
                .filter(this::isDiscountActive)
                .findFirst()
                .map(this::toDiscount)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or inactive discount"));
    }

    @Transactional(readOnly = true)
    public Map<Long, CatalogDiscount> getDiscountsById(List<Long> discountIds) {
        if (discountIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> requested = discountIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CatalogDiscount> discounts = new LinkedHashMap<>();
        for (Product product : productRepository.findProductsWithDiscountIds(requested)) {
            for (Discount discount : product.getDiscounts()) {
                if (requested.contains(discount.getId())) {
                    discounts.put(discount.getId(), toDiscount(discount));
                }
            }
        }
        return discounts;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getPrimaryImagesByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> primaryImages = new LinkedHashMap<>();
        for (Long productId : productIds) {
            primaryImages.put(productId, null);
        }

        for (ProductImageRow row : productRepository.findImageRowsByProductIds(productIds)) {
            primaryImages.computeIfAbsent(row.productId(), k -> row.imageUrl());
        }

        return primaryImages;
    }

    private CatalogCartProduct toCartProduct(Product product) {
        return new CatalogCartProduct(product.getId(), product.getName(), product.getPrice());
    }

    private CatalogDiscount toDiscount(Discount discount) {
        return new CatalogDiscount(
                discount.getId(),
                discount.getDescription(),
                discount.getPercentage(),
                discount.getStartDate(),
                discount.getEndDate()
        );
    }

    private boolean isDiscountActive(Discount discount) {
        LocalDate today = LocalDate.now();
        if (discount.getStartDate() != null && discount.getStartDate().isAfter(today)) {
            return false;
        }
        return discount.getEndDate() == null || !discount.getEndDate().isBefore(today);
    }
}
