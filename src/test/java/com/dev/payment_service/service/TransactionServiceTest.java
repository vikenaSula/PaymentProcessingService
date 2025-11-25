package com.dev.payment_service.service;

import com.dev.payment_service.enums.PaymentMethod;
import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.dev.payment_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setAmount(new BigDecimal("100.00"));
        testTransaction.setCurrency("usd");
        testTransaction.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        testTransaction.setStatus(PaymentStatus.PENDING);
        testTransaction.setProvider("STRIPE");
        testTransaction.setTransactionReference("TXN-123456");
        testTransaction.setIdempotencyKey("idempotency-key-123");
        testTransaction.setProviderReferenceId("pi_test123");
        testTransaction.setCreatedAt(Instant.now());
        testTransaction.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should find transaction by idempotency key")
    void testFindByIdempotencyKey() {
        when(transactionRepository.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.of(testTransaction));
        Optional<Transaction> result = transactionService.findByIdempotencyKey("idempotency-key-123");
        assertTrue(result.isPresent());
        assertEquals(testTransaction.getId(), result.get().getId());
        assertEquals("idempotency-key-123", result.get().getIdempotencyKey());
        verify(transactionRepository).findByIdempotencyKey("idempotency-key-123");
    }

    @Test
    @DisplayName("Should return empty when idempotency key not found")
    void testFindByIdempotencyKeyNotFound() {
        when(transactionRepository.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        Optional<Transaction> result = transactionService.findByIdempotencyKey("non-existent");
        assertFalse(result.isPresent());
        verify(transactionRepository).findByIdempotencyKey("non-existent");
    }

    @Test
    @DisplayName("Should find transaction by provider reference ID")
    void testFindByProviderReferenceId() {
        when(transactionRepository.findByProviderReferenceId(anyString()))
            .thenReturn(Optional.of(testTransaction));
        Optional<Transaction> result = transactionService.findByProviderReferenceId("pi_test123");
        assertTrue(result.isPresent());
        assertEquals("pi_test123", result.get().getProviderReferenceId());
        verify(transactionRepository).findByProviderReferenceId("pi_test123");
    }

    @Test
    @DisplayName("Should find transaction by ID")
    void testFindById() {
        when(transactionRepository.findById(anyLong()))
            .thenReturn(Optional.of(testTransaction));
        Optional<Transaction> result = transactionService.findById(1L);
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        verify(transactionRepository).findById(1L);
    }

    @Test
    @DisplayName("Should find all transactions")
    void testFindAll() {
        Transaction transaction2 = new Transaction();
        transaction2.setId(2L);
        transaction2.setAmount(new BigDecimal("200.00"));
        transaction2.setStatus(PaymentStatus.COMPLETED);

        List<Transaction> transactions = Arrays.asList(testTransaction, transaction2);
        when(transactionRepository.findAll()).thenReturn(transactions);
        List<Transaction> result = transactionService.findAll();
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(transactionRepository).findAll();
    }

    @Test
    @DisplayName("Should create new transaction")
    void testCreateTransaction() {
        when(transactionRepository.save(any(Transaction.class)))
            .thenReturn(testTransaction);
        Transaction result = transactionService.createTransaction(testTransaction);
        assertNotNull(result);
        assertEquals(testTransaction.getId(), result.getId());
        assertEquals(testTransaction.getAmount(), result.getAmount());
        verify(transactionRepository).save(testTransaction);
    }

    @Test
    @DisplayName("Should update existing transaction")
    void testUpdateTransaction() {
        testTransaction.setStatus(PaymentStatus.COMPLETED);
        when(transactionRepository.save(any(Transaction.class)))
            .thenReturn(testTransaction);
        Transaction result = transactionService.updateTransaction(testTransaction);
        assertNotNull(result);
        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getUpdatedAt());
        verify(transactionRepository).save(testTransaction);
    }

    @Test
    @DisplayName("Should update transaction status")
    void testUpdateTransactionStatus() {
        when(transactionRepository.findById(anyLong()))
            .thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class)))
            .thenReturn(testTransaction);
        Transaction result = transactionService.updateTransactionStatus(
            1L, PaymentStatus.COMPLETED, "WEBHOOK_STRIPE");
        assertNotNull(result);
        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertEquals("WEBHOOK_STRIPE", result.getLastModifiedBy());
        assertNotNull(result.getUpdatedAt());
        verify(transactionRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent transaction status")
    void testUpdateTransactionStatusNotFound() {
        when(transactionRepository.findById(anyLong()))
            .thenReturn(Optional.empty());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> transactionService.updateTransactionStatus(999L, PaymentStatus.FAILED, "SYSTEM"));

        assertEquals("Transaction not found: 999", exception.getMessage());
        verify(transactionRepository).findById(999L);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should update transaction status from PENDING to FAILED")
    void testUpdateTransactionStatusToFailed() {
        testTransaction.setStatus(PaymentStatus.PENDING);
        when(transactionRepository.findById(anyLong()))
            .thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionService.updateTransactionStatus(
            1L, PaymentStatus.FAILED, "WEBHOOK_STRIPE");

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        verify(transactionRepository).save(any(Transaction.class));
    }
}

