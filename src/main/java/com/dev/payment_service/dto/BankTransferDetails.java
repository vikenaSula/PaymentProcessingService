package com.dev.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
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
public class BankTransferDetails implements PaymentDetails {

    @JsonProperty("type")
    private final String type = "BANK_TRANSFER";

    @NotBlank(message = "IBAN is required")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]+$", message = "Invalid IBAN format")
    private String iban;

    @NotBlank(message = "Account holder name is required")
    private String accountHolder;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String bankName;
}
