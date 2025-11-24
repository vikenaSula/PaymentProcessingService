package com.dev.payment_service.repository;

import com.dev.payment_service.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByProviderReferenceId(String providerReferenceId);
}
