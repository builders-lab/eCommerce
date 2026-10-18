package com.ecommerce.platform.modules.cart.service;

import com.ecommerce.platform.config.CacheNames;
import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import com.ecommerce.platform.modules.cart.dto.CartItemDiscountDto;
import com.ecommerce.platform.modules.cart.dto.CartItemDto;
import com.ecommerce.platform.modules.cart.dto.CartItemView;
import com.ecommerce.platform.modules.cart.dto.CartResponse;
import com.ecommerce.platform.modules.cart.model.Cart;
import com.ecommerce.platform.modules.cart.model.CartItem;
import com.ecommerce.platform.modules.cart.repository.CartItemRepository;
import com.ecommerce.platform.modules.cart.repository.CartRepository;
import com.ecommerce.platform.modules.catalog.api.CatalogApi;
import com.ecommerce.platform.modules.catalog.api.CatalogCartProduct;
import com.ecommerce.platform.modules.catalog.api.CatalogDiscount;
import jakarta.persistence.OptimisticLockException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CartService {

    private static final BigDecimal EMPLOYEE_DISCOUNT_PERCENTAGE = BigDecimal.valueOf(15);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_MUTATION_ATTEMPTS = 2;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogApi catalogApi;
    private final TransactionTemplate transactionTemplate;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       CatalogApi catalogApi,
                       PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.catalogApi = catalogApi;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse getCart(AuthenticatedUser user) {
        return buildCartResponse(user);
    }

    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse addOrIncrement(AuthenticatedUser user, Long productId, int quantity, Long discountId) {
        return withMutationRetry(() -> addOrIncrementInTransaction(user, productId, quantity, discountId));
    }

    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse setQuantity(AuthenticatedUser user, Long productId, int quantity) {
        return withMutationRetry(() -> setQuantityInTransaction(user, productId, quantity));
    }

    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse removeItem(AuthenticatedUser user, Long productId) {
        return withMutationRetry(() -> removeItemInTransaction(user, productId));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse clear(AuthenticatedUser user) {
        clearByUserId(user.getId());
        return new CartResponse(List.of());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#userId")
    public void clearByUserId(Long userId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteByCartId(cart.getId());
        }
    }

    @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#user.id")
    public CartResponse updateItemDiscount(AuthenticatedUser user, Long productId, Long discountId) {
        return withMutationRetry(() -> updateItemDiscountInTransaction(user, productId, discountId));
    }

    @Transactional
    protected CartResponse addOrIncrementInTransaction(AuthenticatedUser user, Long productId, int quantity, Long discountId) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be > 0");
        }

        Cart cart = getOrCreateCartForMutation(user.getId());
        catalogApi.requireProductExists(productId);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setSelectedDiscountId(resolveSelectedDiscountId(productId, discountId, true));
        } else {
            item.setQuantity(item.getQuantity() + quantity);
            if (discountId != null) {
                item.setSelectedDiscountId(resolveSelectedDiscountId(productId, discountId, false));
            }
        }

        cartItemRepository.saveAndFlush(item);
        return buildCartResponse(user);
    }

    @Transactional
    protected CartResponse setQuantityInTransaction(AuthenticatedUser user, Long productId, int quantity) {
        Cart cart = getOrCreateCartForMutation(user.getId());
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            item.setQuantity(quantity);
            cartItemRepository.saveAndFlush(item);
        }

        return buildCartResponse(user);
    }

    @Transactional
    protected CartResponse removeItemInTransaction(AuthenticatedUser user, Long productId) {
        Cart cart = getOrCreateCartForMutation(user.getId());
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        return buildCartResponse(user);
    }

    @Transactional
    protected CartResponse updateItemDiscountInTransaction(AuthenticatedUser user, Long productId, Long discountId) {
        Cart cart = getOrCreateCartForMutation(user.getId());
        catalogApi.requireProductExists(productId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));

        item.setSelectedDiscountId(resolveSelectedDiscountId(productId, discountId, false));
        cartItemRepository.saveAndFlush(item);
        return buildCartResponse(user);
    }

    private Cart getOrCreateCartForMutation(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> createCart(userId));
    }

    private Cart createCart(Long userId) {
        try {
            Cart cart = new Cart();
            cart.setUserId(userId);
            return cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException ex) {
            return cartRepository.findByUserIdForUpdate(userId).orElseThrow(() -> ex);
        }
    }

    private CartResponse buildCartResponse(AuthenticatedUser user) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderById(user.getId());
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, CatalogCartProduct> products = catalogApi.getProductsForCart(productIds);
        Map<Long, CatalogDiscount> discounts = catalogApi.getDiscountsById(
                cartItems.stream()
                        .map(CartItem::getSelectedDiscountId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        List<CartItemView> items = cartItems.stream()
                .map(item -> toCartItemView(item, products.get(item.getProductId()), discounts.get(item.getSelectedDiscountId())))
                .filter(Objects::nonNull)
                .toList();

        if (items.isEmpty()) {
            return new CartResponse(List.of());
        }

        Map<Long, String> primaryImages = new LinkedHashMap<>(catalogApi.getPrimaryImagesByProductIds(
                items.stream().map(CartItemView::productId).distinct().toList()
        ));

        List<CartItemDto> itemDtos = items.stream()
                .map(item -> toCartItemDto(item, primaryImages.get(item.productId()), user))
                .toList();
        return new CartResponse(itemDtos);
    }

    private CartItemView toCartItemView(CartItem item, CatalogCartProduct product, CatalogDiscount discount) {
        if (product == null) {
            return null;
        }
        return new CartItemView(
                product.id(),
                product.name(),
                product.price(),
                item.getQuantity(),
                discount != null ? discount.id() : null,
                discount != null ? discount.description() : null,
                discount != null ? discount.percentage() : null,
                discount != null ? discount.startDate() : null,
                discount != null ? discount.endDate() : null
        );
    }

    private CartItemDto toCartItemDto(CartItemView item, String primaryImageUrl, AuthenticatedUser user) {
        CatalogDiscount activeSelected = getActiveSelectedDiscount(item);
        BigDecimal productDiscountPercentage = activeSelected != null ? activeSelected.percentage() : BigDecimal.ZERO;
        BigDecimal userDiscountPercentage = getActiveUserDiscountPercentage(user);
        BigDecimal employeeDiscountPercentage = isEmployee(user)
                ? EMPLOYEE_DISCOUNT_PERCENTAGE
                : BigDecimal.ZERO;
        BigDecimal totalDiscountPercentage = calculateTotalDiscountPercentage(
                productDiscountPercentage,
                userDiscountPercentage,
                employeeDiscountPercentage
        );

        BigDecimal basePrice = item.productPrice();
        BigDecimal finalPrice = calculateFinalPrice(basePrice, totalDiscountPercentage);

        return new CartItemDto(
                item.productId(),
                item.productName(),
                basePrice,
                finalPrice,
                primaryImageUrl,
                item.quantity(),
                mapDiscount(activeSelected),
                userDiscountPercentage,
                employeeDiscountPercentage,
                totalDiscountPercentage
        );
    }

    private BigDecimal calculateTotalDiscountPercentage(BigDecimal productDiscount,
                                                        BigDecimal userDiscount,
                                                        BigDecimal employeeDiscount) {
        BigDecimal combined = productDiscount.add(userDiscount).add(employeeDiscount);
        return combined.min(ONE_HUNDRED).max(BigDecimal.ZERO);
    }

    private BigDecimal calculateFinalPrice(BigDecimal basePrice, BigDecimal totalDiscountPercentage) {
        BigDecimal multiplier = BigDecimal.ONE.subtract(totalDiscountPercentage.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private Long resolveSelectedDiscountId(Long productId, Long discountId, boolean applyBestIfMissing) {
        if (discountId == null) {
            CatalogDiscount discount = applyBestIfMissing ? catalogApi.getBestActiveDiscount(productId) : null;
            return discount != null ? discount.id() : null;
        }
        if (discountId == 0) {
            return null;
        }
        return catalogApi.getActiveDiscount(productId, discountId).id();
    }

    private CatalogDiscount getActiveSelectedDiscount(CartItemView selected) {
        if (selected.selectedDiscountId() == null) {
            return null;
        }
        CatalogDiscount discount = new CatalogDiscount(
                selected.selectedDiscountId(),
                selected.selectedDiscountDescription(),
                selected.selectedDiscountPercentage(),
                selected.selectedDiscountStartDate(),
                selected.selectedDiscountEndDate()
        );
        return isDiscountActive(discount) ? discount : null;
    }

    private boolean isDiscountActive(CatalogDiscount discount) {
        LocalDate today = LocalDate.now();
        if (discount.startDate() != null && discount.startDate().isAfter(today)) {
            return false;
        }
        if (discount.endDate() != null && discount.endDate().isBefore(today)) {
            return false;
        }
        return true;
    }

    private boolean isEmployee(AuthenticatedUser user) {
        return user.hasRole("ROLE_EMPLOYEE");
    }

    private BigDecimal getActiveUserDiscountPercentage(AuthenticatedUser user) {
        BigDecimal percentage = user.getUserDiscountPercentage();
        if (percentage == null || percentage.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        LocalDate today = LocalDate.now();
        LocalDate start = user.getUserDiscountStartDate();
        LocalDate end = user.getUserDiscountEndDate();

        if (start == null || start.isAfter(today)) {
            return BigDecimal.ZERO;
        }
        if (end != null && end.isBefore(today)) {
            return BigDecimal.ZERO;
        }
        return percentage;
    }

    private CartItemDiscountDto mapDiscount(CatalogDiscount discount) {
        if (discount == null) {
            return null;
        }
        return new CartItemDiscountDto(
                discount.id(),
                discount.description(),
                discount.percentage(),
                discount.startDate(),
                discount.endDate()
        );
    }

    private CartResponse withMutationRetry(CartMutation mutation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_MUTATION_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> mutation.execute());
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | DataIntegrityViolationException ex) {
                lastFailure = ex;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart was modified concurrently. Please retry.", lastFailure);
    }

    @FunctionalInterface
    private interface CartMutation {
        CartResponse execute();
    }
}
