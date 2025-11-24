package com.dev.payment_service.controller;

import com.dev.payment_service.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/payment")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("Received Stripe webhook event");

        Event event;

        // Verify webhook signature for security (production)
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            try {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
                log.info("Webhook signature verified successfully");
            } catch (SignatureVerificationException e) {
                log.error("Invalid webhook signature: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }
        } else {
            // For development/testing parse without signature verification
            log.warn("Webhook secret not configured - skipping signature verification (NOT SAFE FOR PRODUCTION!)");
            try {
                event = objectMapper.readValue(payload, Event.class);
            } catch (Exception e) {
                log.error("Failed to parse webhook payload: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
            }
        }

        try {
            webhookService.handleWebhookEvent(event);
            log.info("Webhook event processed successfully: type={}, id={}", event.getType(), event.getId());
            return ResponseEntity.ok("Webhook received");
        } catch (Exception e) {
            log.error("Error processing webhook event: type={}, id={}, error={}",
                    event.getType(), event.getId(), e.getMessage(), e);

            // Return 200 anyway to prevent Stripe from retrying
            return ResponseEntity.ok("Webhook received but processing failed");
        }
    }
}

