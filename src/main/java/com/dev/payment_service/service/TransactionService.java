package com.dev.payment_service.service;

import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.dev.payment_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;


    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }


    public Optional<Transaction> findByProviderReferenceId(String providerReferenceId) {
        return transactionRepository.findByProviderReferenceId(providerReferenceId);
    }


    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }


    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }


    @Transactional
    public Transaction updateTransaction(Transaction transaction) {
        transaction.setUpdatedAt(Instant.now());
        return transactionRepository.save(transaction);
    }


    @Transactional
    public Transaction updateTransactionStatus(Long transactionId, PaymentStatus newStatus, String modifiedBy) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        transaction.setStatus(newStatus);
        transaction.setUpdatedAt(Instant.now());
        transaction.setLastModifiedBy(modifiedBy);

        return transactionRepository.save(transaction);
    }

}
