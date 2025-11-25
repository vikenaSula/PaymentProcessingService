package com.dev.payment_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Payment initiation response")
public class PaymentInitiationResponse {

    @Schema(description = "Unique transaction identifier", example = "12345")
    private String transactionId;

    @Schema(description = "Transaction reference number", example = "TXN-20231125-001")
    private String transactionReference;

    @Schema(description = "Payment amount", example = "99.99")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Payment status", example = "COMPLETED")
    private String status;

    @Schema(description = "Payment method used", example = "CREDIT_CARD")
    private String paymentMethod;

    @Schema(description = "Transaction creation timestamp", example = "2023-11-25T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Payment provider name", example = "Stripe")
    private String provider;

    @Schema(description = "Provider's reference ID", example = "pi_3MHzxA2eZvKYlo2C0123456")
    private String providerReferenceId;

}
