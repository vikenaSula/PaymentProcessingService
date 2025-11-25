package com.dev.payment_service.controller;

import com.dev.payment_service.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Webhooks", description = "Webhook endpoints for payment provider callbacks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/payment")
    @Operation(
            summary = "Handle Stripe webhook events",
            description = "Receives and processes webhook events from Stripe payment provider. This endpoint is called by Stripe to notify about payment status changes."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook received and processed"),
            @ApiResponse(responseCode = "400", description = "Invalid signature or payload")
    })
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody
            @Parameter(description = "Stripe webhook event payload", required = true)
            String payload,
            @RequestHeader("Stripe-Signature")
            @Parameter(description = "Stripe signature for webhook verification", required = true)
            String sigHeader) {

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

