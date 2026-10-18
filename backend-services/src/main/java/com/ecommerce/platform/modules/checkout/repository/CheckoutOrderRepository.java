package com.ecommerce.platform.modules.checkout.repository;

import com.ecommerce.platform.modules.checkout.model.CheckoutOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutOrderRepository extends JpaRepository<CheckoutOrder, Long> {

    @Query("select o from CheckoutOrder o left join fetch o.paymentTransaction where o.id = :id")
    Optional<CheckoutOrder> findDetailedById(@Param("id") Long id);
}
