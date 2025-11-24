package com.dev.payment_service.service;

import com.dev.payment_service.dto.CreditCardDetails;
import com.dev.payment_service.dto.BankTransferDetails;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);


    public PaymentIntent processCreditCardPayment(BigDecimal amount, String currency,
                                                   CreditCardDetails cardDetails,
                                                   String idempotencyKey) throws StripeException {

        log.info("Processing credit card payment: amount={}, currency={}", amount, currency);

        // Use Stripe test token (paymentMethodId) - NEVER accept raw card numbers
        String paymentMethodId = cardDetails.getPaymentMethodId();

        if (paymentMethodId == null || paymentMethodId.isEmpty()) {
            throw new IllegalArgumentException("Payment method ID is required. Use Stripe test tokens like 'pm_card_visa'");
        }

        log.info("Using Stripe payment method: {}", paymentMethodId);

        // Convert amount to cents (Stripe requires smallest currency unit)
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        log.info("Creating PaymentIntent: amount={} cents, currency={}, paymentMethodId={}, idempotencyKey={}",
                amountInCents, currency.toLowerCase(), paymentMethodId, idempotencyKey);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                )
                .setDescription("Payment transaction")
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params,
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());

            log.info("Payment Intent created successfully: id={}, status={}", intent.getId(), intent.getStatus());
            return intent;

        } catch (StripeException e) {
            log.error("Failed to create PaymentIntent: errorCode={}, message={}, statusCode={}, requestId={}",
                    e.getCode(), e.getMessage(), e.getStatusCode(), e.getRequestId(), e);
            throw e;
        }
    }

    /** Process bank transfer payment through Stripe SEPA Direct Debit*/
    public PaymentIntent processBankTransferPayment(BigDecimal amount, String currency,
                                                     BankTransferDetails bankDetails,
                                                     String idempotencyKey) throws StripeException {

        log.info("Processing bank transfer payment: amount={}, currency={}, iban={}",
                amount, currency);

        PaymentMethod paymentMethod = createBankPaymentMethod(bankDetails);

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        log.info("Creating Bank Transfer PaymentIntent: amount={} cents, currency={}, paymentMethodId={}, idempotencyKey={}",
                amountInCents, currency.toLowerCase(), paymentMethod.getId(), idempotencyKey);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .setPaymentMethod(paymentMethod.getId())
                .addPaymentMethodType("sepa_debit")
                .setConfirm(true)
                .setMandateData(
                        PaymentIntentCreateParams.MandateData.builder()
                                .setCustomerAcceptance(
                                        PaymentIntentCreateParams.MandateData.CustomerAcceptance.builder()
                                                .setType(PaymentIntentCreateParams.MandateData.CustomerAcceptance.Type.ONLINE)
                                                .setOnline(
                                                        PaymentIntentCreateParams.MandateData.CustomerAcceptance.Online.builder()
                                                                .setIpAddress("127.0.0.1") // In production, use real customer IP
                                                                .setUserAgent("PaymentService/1.0") // In production, use real user agent
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .setDescription("Bank transfer payment")
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params,
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());

            log.info("Bank Payment Intent created successfully: id={}, status={}", intent.getId(), intent.getStatus());
            return intent;

        } catch (StripeException e) {
            log.error("Failed to create Bank Transfer PaymentIntent: errorCode={}, message={}, statusCode={}, requestId={}",
                    e.getCode(), e.getMessage(), e.getStatusCode(), e.getRequestId(), e);
            throw e;
        }
    }


    private PaymentMethod createBankPaymentMethod(BankTransferDetails bankDetails) throws StripeException {

        PaymentMethodCreateParams params = PaymentMethodCreateParams.builder()
                .setType(PaymentMethodCreateParams.Type.SEPA_DEBIT)
                .setSepaDebit(PaymentMethodCreateParams.SepaDebit.builder()
                        .setIban(bankDetails.getIban())
                        .build())
                .setBillingDetails(PaymentMethodCreateParams.BillingDetails.builder()
                        .setName(bankDetails.getAccountHolder())
                        .setEmail(bankDetails.getEmail())
                        .build())
                .build();

        return PaymentMethod.create(params);
    }

}
