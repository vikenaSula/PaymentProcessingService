package com.dev.payment_service.dto;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreditCardDetails.class, name = "CREDIT_CARD"),
        @JsonSubTypes.Type(value = BankTransferDetails.class, name = "BANK_TRANSFER")
})

public interface PaymentDetails {
}
