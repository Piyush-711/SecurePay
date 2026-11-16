package com.securepay.repository;

import com.securepay.domain.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByIdAndMerchantId(UUID id, UUID merchantId);
    List<PaymentTransaction> findByMerchantId(UUID merchantId);
}
