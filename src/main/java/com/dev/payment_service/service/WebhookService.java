package com.dev.payment_service.service;

import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final TransactionService transactionService;

    @Transactional
    public void handleWebhookEvent(Event event) {
        log.info("Processing webhook event: type={}, id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled" -> handlePaymentIntentCanceled(event);
            case "payment_intent.processing" -> handlePaymentIntentProcessing(event);
            case "charge.refunded" -> handleChargeRefunded(event);
            default -> log.info("Unhandled event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) return;

        String stripePaymentIntentId = paymentIntent.getId();
        log.info("Payment succeeded: stripeId={}, amount={}", stripePaymentIntentId, paymentIntent.getAmount());

        Optional<Transaction> txOpt = transactionService.findByProviderReferenceId(stripePaymentIntentId);

        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            transactionService.updateTransactionStatus(tx.getId(), PaymentStatus.COMPLETED, "WEBHOOK_STRIPE");
            log.info("Transaction updated to COMPLETED: id={}, reference={}", tx.getId(), tx.getTransactionReference());
        } else {
            log.warn("Transaction not found for Stripe PaymentIntent: {}", stripePaymentIntentId);
        }
    }
    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) return;

        String stripePaymentIntentId = paymentIntent.getId();
        log.warn("Payment failed: stripeId={}", stripePaymentIntentId);

        Optional<Transaction> txOpt = transactionService.findByProviderReferenceId(stripePaymentIntentId);

        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            transactionService.updateTransactionStatus(tx.getId(), PaymentStatus.FAILED, "WEBHOOK_STRIPE");
            log.info("Transaction updated to FAILED: id={}, reference={}", tx.getId(), tx.getTransactionReference());
        } else {
            log.warn("Transaction not found for Stripe PaymentIntent: {}", stripePaymentIntentId);
        }
    }

    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) return;

        String stripePaymentIntentId = paymentIntent.getId();
        log.info("Payment canceled: stripeId={}", stripePaymentIntentId);

        Optional<Transaction> txOpt = transactionService.findByProviderReferenceId(stripePaymentIntentId);

        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            transactionService.updateTransactionStatus(tx.getId(), PaymentStatus.FAILED, "WEBHOOK_STRIPE");
            log.info("Transaction updated to FAILED (canceled): id={}, reference={}", tx.getId(), tx.getTransactionReference());
        } else {
            log.warn("Transaction not found for Stripe PaymentIntent: {}", stripePaymentIntentId);
        }
    }

    private void handlePaymentIntentProcessing(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) return;

        String stripePaymentIntentId = paymentIntent.getId();
        log.info("Payment processing: stripeId={}", stripePaymentIntentId);

        Optional<Transaction> txOpt = transactionService.findByProviderReferenceId(stripePaymentIntentId);

        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            if (tx.getStatus() == PaymentStatus.PENDING) {
                log.info("Transaction remains PENDING (processing): id={}, reference={}", tx.getId(), tx.getTransactionReference());
            }
        } else {
            log.warn("Transaction not found for Stripe PaymentIntent: {}", stripePaymentIntentId);
        }
    }


    private void handleChargeRefunded(Event event) {
        log.info("Charge refunded event received: {}", event.getId());
        // TODO: Implement refund handling
    }


    private PaymentIntent extractPaymentIntent(Event event) {
        try {
            return (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to extract PaymentIntent from event: {}", e.getMessage(), e);
            return null;
        }
    }
}

