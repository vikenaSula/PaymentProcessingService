package com.dev.payment_service.service;

import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.dev.payment_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
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

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }


    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("User {} created transaction: ID={}, Reference={}, Amount={}, Currency={}, Method={}, Status={}",
                savedTransaction.getCreatedBy(),
                savedTransaction.getId(),
                savedTransaction.getTransactionReference(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getPaymentMethod(),
                savedTransaction.getStatus());
        return savedTransaction;
    }


    @Transactional
    public Transaction updateTransaction(Transaction transaction) {
        transaction.setUpdatedAt(Instant.now());
        Transaction updatedTransaction = transactionRepository.save(transaction);
        log.info("User {} modified transaction: ID={}, Reference={}, Status={}",
                updatedTransaction.getLastModifiedBy(),
                updatedTransaction.getId(),
                updatedTransaction.getTransactionReference(),
                updatedTransaction.getStatus());
        return updatedTransaction;
    }


    @Transactional
    public Transaction updateTransactionStatus(Long transactionId, PaymentStatus newStatus, String modifiedBy) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        PaymentStatus oldStatus = transaction.getStatus();
        transaction.setStatus(newStatus);
        transaction.setUpdatedAt(Instant.now());
        transaction.setLastModifiedBy(modifiedBy);

        Transaction updatedTransaction = transactionRepository.save(transaction);
        log.info("User {} modified transaction: ID={}, Reference={}, NewStatus={}",
                modifiedBy,
                updatedTransaction.getId(),
                updatedTransaction.getTransactionReference(),
                newStatus);
        return updatedTransaction;
    }

}
