package com.dev.payment_service.controller;

import com.dev.payment_service.dto.CreditCardDetails;
import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.enums.PaymentMethod;
import com.dev.payment_service.enums.UserRole;
import com.dev.payment_service.model.User;
import com.dev.payment_service.repository.UserRepository;
import com.dev.payment_service.security.JwtUtil;
import com.dev.payment_service.service.StripeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Payment Initiation Integration Tests")
class PaymentInitiationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private StripeService stripeService;

    private String customerToken;
    private String adminToken;
    private User customerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        customerUser = new User();
        customerUser.setUsername("customer@test.com");
        customerUser.setPassword(passwordEncoder.encode("password123"));
        customerUser.setUserRole(UserRole.CUSTOMER);
        customerUser = userRepository.save(customerUser);

        adminUser = new User();
        adminUser.setUsername("admin@test.com");
        adminUser.setPassword(passwordEncoder.encode("password123"));
        adminUser.setUserRole(UserRole.ADMIN);
        adminUser = userRepository.save(adminUser);

        customerToken = jwtUtil.generateToken(customerUser);
        adminToken = jwtUtil.generateToken(adminUser);
    }

    @Test
    @DisplayName("Should successfully initiate payment with valid credit card details")
    void shouldInitiatePaymentWithValidCreditCardDetails() throws Exception {
        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_123456");
        mockPaymentIntent.setStatus("requires_confirmation");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("John Doe");
        creditCardDetails.setExpiryMonth("12");
        creditCardDetails.setExpiryYear("2025");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "test-idempotency-key-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.transactionReference").exists())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("usd"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.providerReferenceId").value("pi_test_123456"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("Should return same response for duplicate idempotency key")
    void shouldReturnSameResponseForDuplicateIdempotencyKey() throws Exception {
        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_duplicate");
        mockPaymentIntent.setStatus("requires_confirmation");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Jane Doe");
        creditCardDetails.setExpiryMonth("11");
        creditCardDetails.setExpiryYear("2026");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("EUR");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        String idempotencyKey = "duplicate-test-key-" + System.currentTimeMillis();

        String firstResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(secondResponse).isEqualTo(firstResponse);
    }

    @Test
    @DisplayName("Should fail when idempotency key is missing")
    void shouldFailWhenIdempotencyKeyIsMissing() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("06");
        creditCardDetails.setExpiryYear("2027");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("75.50"));
        request.setCurrency("GBP");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when amount is negative")
    void shouldFailWhenAmountIsNegative() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("03");
        creditCardDetails.setExpiryYear("2028");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("-10.00"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "negative-amount-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when amount is null")
    void shouldFailWhenAmountIsNull() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("09");
        creditCardDetails.setExpiryYear("2026");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(null);
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "null-amount-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when currency is invalid")
    void shouldFailWhenCurrencyIsInvalid() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("07");
        creditCardDetails.setExpiryYear("2027");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("US");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "invalid-currency-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when payment method is null")
    void shouldFailWhenPaymentMethodIsNull() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("04");
        creditCardDetails.setExpiryYear("2029");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrency("EUR");
        request.setPaymentMethod(null);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "null-method-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when payment details are null")
    void shouldFailWhenPaymentDetailsAreNull() throws Exception {
        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(null);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "null-details-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when authentication token is missing")
    void shouldFailWhenAuthenticationTokenIsMissing() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("10");
        creditCardDetails.setExpiryYear("2025");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "no-auth-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should successfully initiate payment with admin role")
    void shouldInitiatePaymentWithAdminRole() throws Exception {
        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_admin");
        mockPaymentIntent.setStatus("requires_confirmation");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_mastercard");
        creditCardDetails.setCardHolder("Admin User");
        creditCardDetails.setExpiryMonth("05");
        creditCardDetails.setExpiryYear("2028");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("EUR");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "admin-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.currency").value("eur"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should fail when credit card payment method ID is blank")
    void shouldFailWhenCreditCardPaymentMethodIdIsBlank() throws Exception {
        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("");
        creditCardDetails.setCardHolder("Test User");
        creditCardDetails.setExpiryMonth("08");
        creditCardDetails.setExpiryYear("2026");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "blank-pm-id-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }


    // ========== GET /api/v1/payments/{id} Tests ==========

    @Test
    @DisplayName("Should successfully retrieve payment by ID with customer role")
    void shouldRetrievePaymentByIdWithCustomerRole() throws Exception {
        // First, create a payment
        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_retrieve");
        mockPaymentIntent.setStatus("requires_confirmation");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("John Retrieve");
        creditCardDetails.setExpiryMonth("12");
        creditCardDetails.setExpiryYear("2025");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("125.50"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        String createResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "retrieve-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper.readTree(createResponse).get("transactionId").asText();

        mockMvc.perform(get("/api/v1/payments/" + transactionId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.currency").value("usd"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.providerReferenceId").value("pi_test_retrieve"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("Should successfully retrieve payment by ID with admin role")
    void shouldRetrievePaymentByIdWithAdminRole() throws Exception {

        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_admin_retrieve");
        mockPaymentIntent.setStatus("succeeded");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_amex");
        creditCardDetails.setCardHolder("Admin Retrieve");
        creditCardDetails.setExpiryMonth("06");
        creditCardDetails.setExpiryYear("2027");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("350.00"));
        request.setCurrency("EUR");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        String createResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "admin-retrieve-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper.readTree(createResponse).get("transactionId").asText();

        // Admin retrieves the payment
        mockMvc.perform(get("/api/v1/payments/" + transactionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.amount").value(350.00))
                .andExpect(jsonPath("$.currency").value("eur"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"));
    }

    @Test
    @DisplayName("Should fail to retrieve non-existent payment")
    void shouldFailToRetrieveNonExistentPayment() throws Exception {
        Long nonExistentId = 999999L;

        mockMvc.perform(get("/api/v1/payments/" + nonExistentId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should fail to retrieve payment without authentication")
    void shouldFailToRetrievePaymentWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should fail to retrieve payment with invalid ID format")
    void shouldFailToRetrievePaymentWithInvalidIdFormat() throws Exception {
        mockMvc.perform(get("/api/v1/payments/invalid-id")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should retrieve payment multiple times and get same data")
    void shouldRetrievePaymentMultipleTimesWithSameData() throws Exception {

        PaymentIntent mockPaymentIntent = new PaymentIntent();
        mockPaymentIntent.setId("pi_test_idempotent_retrieve");
        mockPaymentIntent.setStatus("requires_confirmation");

        when(stripeService.processCreditCardPayment(any(BigDecimal.class), anyString(), any(), anyString()))
                .thenReturn(mockPaymentIntent);

        CreditCardDetails creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");
        creditCardDetails.setCardHolder("Multiple Retrieve");
        creditCardDetails.setExpiryMonth("03");
        creditCardDetails.setExpiryYear("2026");

        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setAmount(new BigDecimal("75.25"));
        request.setCurrency("GBP");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setDetails(creditCardDetails);

        String createResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "multi-retrieve-test-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper.readTree(createResponse).get("transactionId").asText();

        String firstRetrieve = mockMvc.perform(get("/api/v1/payments/" + transactionId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondRetrieve = mockMvc.perform(get("/api/v1/payments/" + transactionId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(secondRetrieve).isEqualTo(firstRetrieve);
    }
}

