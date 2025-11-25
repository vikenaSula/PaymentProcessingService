package com.dev.payment_service.controller;

import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.dto.PaymentInitiationResponse;
import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.service.PaymentService;
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
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<PaymentInitiationResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiationRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {

        PaymentInitiationResponse response = paymentService.initiatePayment(request, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<PaymentInitiationResponse> getPaymentById(@PathVariable Long id) {
        PaymentInitiationResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentInitiationResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {

        List<PaymentInitiationResponse> payments = paymentService.getAllPayments(
                status, startDate, endDate, minAmount, maxAmount);
        return ResponseEntity.ok(payments);
    }

}
