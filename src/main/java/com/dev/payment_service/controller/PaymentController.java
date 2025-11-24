package com.dev.payment_service.controller;

import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.dto.PaymentInitiationResponse;
import com.dev.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping
    public ResponseEntity<PaymentInitiationResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiationRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {

        PaymentInitiationResponse response = paymentService.initiatePayment(request, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
