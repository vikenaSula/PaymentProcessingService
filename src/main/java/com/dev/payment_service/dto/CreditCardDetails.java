package com.dev.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class CreditCardDetails implements PaymentDetails {

    @JsonProperty("type")
    private final String type = "CREDIT_CARD";

    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;

    private String cardHolder;

    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Expiry month must be 01-12")
    private String expiryMonth;

    @Pattern(regexp = "^[0-9]{4}$", message = "Expiry year must be 4 digits")
    private String expiryYear;
}

