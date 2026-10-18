package com.ecommerce.platform.modules.cart.repository;

import com.ecommerce.platform.modules.cart.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("select ci from CartItem ci where ci.cart.id = :cartId and ci.productId = :productId")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.cart.id = :cartId and ci.productId = :productId")
    void deleteByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);

    @Query("select ci from CartItem ci join ci.cart c where c.userId = :userId order by ci.id")
    List<CartItem> findByUserIdOrderById(@Param("userId") Long userId);
}

