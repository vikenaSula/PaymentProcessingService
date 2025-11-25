package com.dev.payment_service.controller;

import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.dto.PaymentInitiationResponse;
import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment processing endpoints for initiating and retrieving payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(
            summary = "Initiate a new payment",
            description = "Creates a new payment transaction. Supports credit card and bank transfer methods. Requires an idempotency key to prevent duplicate payments."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment successfully initiated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentInitiationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payment request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "409", description = "Duplicate idempotency key", content = @Content)
    })
    public ResponseEntity<PaymentInitiationResponse> initiatePayment(
            @Valid @RequestBody
            @Parameter(description = "Payment initiation request details", required = true)
            PaymentInitiationRequest request,
            @RequestHeader(value = "Idempotency-Key")
            @Parameter(description = "Unique key to prevent duplicate payments", required = true)
            String idempotencyKey) {

        PaymentInitiationResponse response = paymentService.initiatePayment(request, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(
            summary = "Get payment by ID",
            description = "Retrieves a specific payment transaction by its ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentInitiationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    public ResponseEntity<PaymentInitiationResponse> getPaymentById(
            @PathVariable
            @Parameter(description = "Payment transaction ID", required = true)
            Long id) {
        PaymentInitiationResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all payments",
            description = "Retrieves all payments with optional filters. Admin only."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentInitiationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content)
    })
    public ResponseEntity<List<PaymentInitiationResponse>> getAllPayments(
            @RequestParam(required = false)
            @Parameter(description = "Filter by payment status")
            PaymentStatus status,
            @RequestParam(required = false)
            @Parameter(description = "Filter by start date (ISO format)")
            String startDate,
            @RequestParam(required = false)
            @Parameter(description = "Filter by end date (ISO format)")
            String endDate,
            @RequestParam(required = false)
            @Parameter(description = "Filter by minimum amount")
            BigDecimal minAmount,
            @RequestParam(required = false)
            @Parameter(description = "Filter by maximum amount")
            BigDecimal maxAmount) {

        List<PaymentInitiationResponse> payments = paymentService.getAllPayments(
                status, startDate, endDate, minAmount, maxAmount);
        return ResponseEntity.ok(payments);
    }

}


