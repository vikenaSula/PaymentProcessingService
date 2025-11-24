package com.dev.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationResponse {

    private String transactionId;
    private String transactionReference;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private Instant createdAt;

    private String provider;
    private String providerReferenceId;

}
