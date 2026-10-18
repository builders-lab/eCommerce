package com.ecommerce.platform.modules.checkout.repository;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByProviderSessionId(String providerSessionId);

    Optional<PaymentTransaction> findByPaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByProviderAndProviderSessionId(PaymentProvider provider, String providerSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByProviderAndPaymentIntentId(PaymentProvider provider, String paymentIntentId);
}
