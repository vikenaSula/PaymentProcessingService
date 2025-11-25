package com.dev.payment_service.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.isEmpty() || !secretKey.startsWith("sk_")) {
            log.error("Invalid Stripe secret key! Key must start with 'sk_' ");
            throw new IllegalStateException("Invalid Stripe API key configuration");
        }

        Stripe.apiKey = secretKey;
    }
}
