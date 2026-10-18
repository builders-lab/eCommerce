package com.ecommerce.platform.modules.catalog.service;

import com.ecommerce.platform.config.CacheNames;
import com.ecommerce.platform.common.dto.PageResponse;
import com.ecommerce.platform.modules.catalog.dto.request.ProductUpsertRequest;
import com.ecommerce.platform.modules.catalog.dto.response.DiscountResponse;
import com.ecommerce.platform.modules.catalog.dto.response.ProductListItemResponse;
import com.ecommerce.platform.modules.catalog.dto.response.ProductResponse;
import com.ecommerce.platform.modules.catalog.model.Product;
import com.ecommerce.platform.modules.catalog.repository.ProductImageRow;
import com.ecommerce.platform.modules.catalog.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.PRODUCT_LISTS,
            key = "{(#category == null || #category.isBlank()) ? 'all' : #category, #page, #size}"
    )
    public PageResponse<ProductListItemResponse> getProducts(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Product> products = (category == null || category.isBlank())
                ? productRepository.findPage(pageable)
                : productRepository.findPageByCategory(category, pageable);

        if (products.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0, false);
        }

        var productPage = products.getContent();
        Map<Long, List<String>> imagesByProductId = loadImagesByProductId(productPage);
        List<ProductListItemResponse> items = productPage.stream()
                .map(product -> toListItemResponse(product, imagesByProductId))
                .toList();

        return new PageResponse<>(
                items,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.hasNext()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse getProductById(Long id) {
        return toDetailResponse(getProductEntityById(id));
    }

    @Transactional(readOnly = true)
    public Product getProductEntityById(Long id) {
        return productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional(readOnly = true)
    public Product getProductForCartMutationById(Long id) {
        return productRepository.findForCartMutationById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'all'")
    public List<String> getAllCategories() {
        return productRepository.findDistinctCategories();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    })
    public ProductResponse createProduct(ProductUpsertRequest request) {
        Product product = new Product();
        applyUpsertRequest(product, request);
        return toDetailResponse(productRepository.save(product));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_IMAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_DISCOUNTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, allEntries = true)
    })
    public ProductResponse updateProduct(Long id, ProductUpsertRequest request) {
        Product existing = getProductEntityById(id);
        applyUpsertRequest(existing, request);
        return toDetailResponse(productRepository.save(existing));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_IMAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_REVIEWS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_DISCOUNTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, allEntries = true)
    })
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        productRepository.deleteById(id);
    }

    private void applyUpsertRequest(Product product, ProductUpsertRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setImages(request.images());
    }

    public ProductListItemResponse toListItemResponse(Product product, Map<Long, List<String>> imagesByProductId) {
        return new ProductListItemResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                imagesByProductId.getOrDefault(product.getId(), List.of())
        );
    }

    public ProductResponse toDetailResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getImages(),
                product.getDiscounts().stream()
                        .map(discount -> new DiscountResponse(
                                discount.getId(),
                                discount.getDescription(),
                                discount.getPercentage(),
                                discount.getStartDate(),
                                discount.getEndDate()
                ))
                .toList()
        );
    }

    private Map<Long, List<String>> loadImagesByProductId(List<Product> products) {
        List<Long> productIds = products.stream()
                .map(product -> Objects.requireNonNull(product.getId(), "Product id must be present for persisted products"))
                .toList();

        Map<Long, List<String>> imagesByProductId = new LinkedHashMap<>();
        for (Long productId : productIds) {
            imagesByProductId.put(productId, List.of());
        }

        Map<Long, List<String>> groupedImages = productRepository.findImageRowsByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        ProductImageRow::productId,
                        LinkedHashMap::new,
                        Collectors.mapping(ProductImageRow::imageUrl, Collectors.toList())
                ));

        imagesByProductId.putAll(groupedImages);
        return imagesByProductId;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_IMAGES, key = "#productIds", unless = "#productIds.isEmpty()")
    public Map<Long, String> getPrimaryImagesByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> primaryImages = new LinkedHashMap<>();
        for (Long productId : productIds) {
            primaryImages.put(productId, null);
        }

        for (ProductImageRow row : productRepository.findImageRowsByProductIds(productIds)) {
            if (primaryImages.get(row.productId()) == null) {
                primaryImages.put(
                        row.productId(),
                        row.imageUrl()
                );
            }
        }

        return primaryImages;
    }
}
