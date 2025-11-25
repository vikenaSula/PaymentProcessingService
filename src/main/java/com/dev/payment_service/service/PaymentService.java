package com.dev.payment_service.service;

import com.dev.payment_service.dto.BankTransferDetails;
import com.dev.payment_service.dto.CreditCardDetails;
import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.dto.PaymentInitiationResponse;
import com.dev.payment_service.enums.PaymentMethod;
import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionService transactionService;
    private final StripeService stripeService;

    @Transactional
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key header is required and cannot be empty");
        }

        Optional<Transaction> existing = transactionService.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate request detected with idempotency key: {}", idempotencyKey);
            return mapResponse(existing.get());
        }

        Transaction tx = new Transaction();
        tx.setAmount(request.getAmount());
        tx.setCurrency(request.getCurrency().toLowerCase());
        tx.setPaymentMethod(request.getPaymentMethod());
        tx.setStatus(PaymentStatus.PENDING);
        tx.setProvider("STRIPE");
        tx.setTransactionReference(generateTransactionReference());
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        tx.setIdempotencyKey(idempotencyKey);

        tx = transactionService.createTransaction(tx);

        log.info("Transaction created: id={}, reference={}, method={}",
                tx.getId(), tx.getTransactionReference(), tx.getPaymentMethod());

        if (request.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
            handleCardPayment(tx, request, idempotencyKey);
        } else if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            handleBankPayment(tx, request, idempotencyKey);
        }

        return mapResponse(tx);
    }

    public PaymentInitiationResponse getPaymentById(Long id) {
        Transaction transaction = transactionService.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found with id: " + id));

        return mapResponse(transaction);
    }

    public List<PaymentInitiationResponse> getAllPayments(
            PaymentStatus status,
            String startDateStr,
            String endDateStr,
            BigDecimal minAmount,
            BigDecimal maxAmount) {

        LocalDate startDate = null;
        LocalDate endDate = null;

        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr.trim());
            } catch (Exception e) {
                log.error("Invalid startDate format received: '{}'. Error: {}. Expected format: yyyy-MM-dd (e.g., 2025-11-24)",
                        startDateStr, e.getMessage());
                throw new IllegalArgumentException(
                        String.format("Invalid startDate format: '%s'. Expected format: yyyy-MM-dd (e.g., 2025-11-24)", startDateStr));
            }
        }

        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr.trim());
            } catch (Exception e) {
                log.error("Invalid endDate format received: '{}'. Error: {}. Expected format: yyyy-MM-dd (e.g., 2025-11-24)",
                        endDateStr, e.getMessage());
                throw new IllegalArgumentException(
                        String.format("Invalid endDate format: '%s'. Expected format: yyyy-MM-dd (e.g., 2025-11-24)", endDateStr));
            }
        }

        List<Transaction> transactions = transactionService.findAll();

        List<Transaction> filteredTransactions = new ArrayList<>();

        for (Transaction transaction : transactions) {
            if (!matchesFilters(transaction, status, startDate, endDate, minAmount, maxAmount)) {
                continue;
            }
            filteredTransactions.add(transaction);
        }

        List<PaymentInitiationResponse> responses = new ArrayList<>();
        for (Transaction transaction : filteredTransactions) {
            responses.add(mapResponse(transaction));
        }

        return responses;
    }

    private boolean matchesFilters(
            Transaction transaction,
            PaymentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount) {

        if (status != null && transaction.getStatus() != status) {
            return false;
        }

        if (startDate != null) {
            Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            if (transaction.getCreatedAt().isBefore(startInstant)) {
                return false;
            }
        }

        if (endDate != null) {
            Instant endInstant = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            if (transaction.getCreatedAt().isAfter(endInstant) ||
                    transaction.getCreatedAt().equals(endInstant)) {
                return false;
            }
        }
        if (minAmount != null && transaction.getAmount().compareTo(minAmount) < 0) {
            return false;
        }

        if (maxAmount != null && transaction.getAmount().compareTo(maxAmount) > 0) {
            return false;
        }

        return true;
    }

    private void handleCardPayment(Transaction tx, PaymentInitiationRequest request, String idempotencyKey) {

        if (!(request.getDetails() instanceof CreditCardDetails)) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setProviderReferenceId("INVALID_CARD_DETAILS");
            tx.setUpdatedAt(Instant.now());
            transactionService.updateTransaction(tx);
            log.error("Invalid card details for transaction: {}", tx.getId());
            return;
        }

        CreditCardDetails cardDetails = (CreditCardDetails) request.getDetails();

        try {
            PaymentIntent intent = stripeService.processCreditCardPayment(
                    tx.getAmount(),
                    tx.getCurrency(),
                    cardDetails,
                    idempotencyKey
            );

            tx.setProviderReferenceId(intent.getId());
            tx.setUpdatedAt(Instant.now());

            String stripeStatus = intent.getStatus();
            switch (stripeStatus) {
                case "succeeded" -> {
                    tx.setStatus(PaymentStatus.COMPLETED);
                    log.info("Card payment succeeded: transactionId={}, stripeId={}", tx.getId(), intent.getId());
                }
                case "processing", "requires_action", "requires_confirmation" -> {
                    tx.setStatus(PaymentStatus.PENDING);
                    log.info("Card payment pending: transactionId={}, stripeStatus={}", tx.getId(), stripeStatus);
                }
                default -> {
                    tx.setStatus(PaymentStatus.FAILED);
                    log.warn("Card payment failed: transactionId={}, stripeStatus={}", tx.getId(), stripeStatus);
                }
            }

            transactionService.updateTransaction(tx);

        } catch (StripeException e) {
            tx.setStatus(PaymentStatus.FAILED);
            String errorCode = e.getCode() != null ? e.getCode() : "UNKNOWN";
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown Stripe error";
            tx.setProviderReferenceId("STRIPE_ERROR:" + errorCode);
            tx.setUpdatedAt(Instant.now());
            transactionService.updateTransaction(tx);
            log.error("Stripe error processing card payment: transactionId={}, errorCode={}, errorMessage={}, stripeCode={}",
                    tx.getId(), errorCode, errorMessage, e.getStripeError() != null ? e.getStripeError().getCode() : "null", e);
        }
    }

    private void handleBankPayment(Transaction tx, PaymentInitiationRequest request, String idempotencyKey) {

        if (!(request.getDetails() instanceof BankTransferDetails)) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setProviderReferenceId("INVALID_BANK_DETAILS");
            tx.setUpdatedAt(Instant.now());
            transactionService.updateTransaction(tx);
            log.error("Invalid bank details for transaction: {}", tx.getId());
            return;
        }

        BankTransferDetails bankDetails = (BankTransferDetails) request.getDetails();

        try {
            PaymentIntent intent = stripeService.processBankTransferPayment(
                    tx.getAmount(),
                    tx.getCurrency(),
                    bankDetails,
                    idempotencyKey
            );

            tx.setProviderReferenceId(intent.getId());
            tx.setUpdatedAt(Instant.now());

            // Map Stripe status to our status
            String stripeStatus = intent.getStatus();
            switch (stripeStatus) {
                case "succeeded" -> {
                    tx.setStatus(PaymentStatus.COMPLETED);
                    log.info("Bank payment succeeded: transactionId={}, stripeId={}", tx.getId(), intent.getId());
                }
                case "processing", "requires_action", "requires_confirmation" -> {
                    tx.setStatus(PaymentStatus.PENDING);
                    log.info("Bank payment pending: transactionId={}, stripeStatus={}", tx.getId(), stripeStatus);
                }
                default -> {
                    tx.setStatus(PaymentStatus.FAILED);
                    log.warn("Bank payment failed: transactionId={}, stripeStatus={}", tx.getId(), stripeStatus);
                }
            }

            transactionService.updateTransaction(tx);

        } catch (StripeException e) {
            tx.setStatus(PaymentStatus.FAILED);
            String errorCode = e.getCode() != null ? e.getCode() : "UNKNOWN";
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown Stripe error";
            tx.setProviderReferenceId("STRIPE_ERROR:" + errorCode);
            tx.setUpdatedAt(Instant.now());
            transactionService.updateTransaction(tx);
            log.error("Stripe error processing bank transfer: transactionId={}, errorCode={}, errorMessage={}, stripeCode={}",
                    tx.getId(), errorCode, errorMessage, e.getStripeError() != null ? e.getStripeError().getCode() : "null", e);
        }
    }

    private PaymentInitiationResponse mapResponse(Transaction transaction) {
        PaymentInitiationResponse response = new PaymentInitiationResponse();
        response.setTransactionId(transaction.getId().toString());
        response.setTransactionReference(transaction.getTransactionReference());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setStatus(transaction.getStatus().name());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setProvider(transaction.getProvider());
        response.setPaymentMethod(transaction.getPaymentMethod().name());
        response.setProviderReferenceId(transaction.getProviderReferenceId());
        return response;
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
