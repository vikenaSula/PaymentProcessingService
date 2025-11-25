package com.dev.payment_service.service;

import com.dev.payment_service.dto.BankTransferDetails;
import com.dev.payment_service.dto.CreditCardDetails;
import com.dev.payment_service.dto.PaymentInitiationRequest;
import com.dev.payment_service.dto.PaymentInitiationResponse;
import com.dev.payment_service.enums.PaymentMethod;
import com.dev.payment_service.enums.PaymentStatus;
import com.dev.payment_service.model.Transaction;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private StripeService stripeService;

    @InjectMocks
    private PaymentService paymentService;

    private Transaction testTransaction;
    private PaymentInitiationRequest paymentRequest;
    private CreditCardDetails creditCardDetails;
    private BankTransferDetails bankTransferDetails;
    private PaymentIntent paymentIntent;

    @BeforeEach
    void setUp() {
        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setAmount(new BigDecimal("100.00"));
        testTransaction.setCurrency("usd");
        testTransaction.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        testTransaction.setStatus(PaymentStatus.PENDING);
        testTransaction.setProvider("STRIPE");
        testTransaction.setTransactionReference("TXN-12345678");
        testTransaction.setIdempotencyKey("idempotency-key-123");
        testTransaction.setProviderReferenceId("pi_test123");
        testTransaction.setCreatedAt(Instant.now());
        testTransaction.setUpdatedAt(Instant.now());

        creditCardDetails = new CreditCardDetails();
        creditCardDetails.setPaymentMethodId("pm_card_visa");

        bankTransferDetails = new BankTransferDetails();
        bankTransferDetails.setIban("DE89370400440532013000");
        bankTransferDetails.setAccountHolder("John Doe");
        bankTransferDetails.setEmail("john@example.com");

        paymentRequest = new PaymentInitiationRequest();
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        paymentRequest.setDetails(creditCardDetails);

        paymentIntent = new PaymentIntent();
        paymentIntent.setId("pi_test123");
        paymentIntent.setAmount(10000L);
        paymentIntent.setStatus("succeeded");
    }

    @Test
    @DisplayName("Should throw exception when idempotency key is null")
    void testInitiatePaymentNullIdempotencyKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> paymentService.initiatePayment(paymentRequest, null));

        assertEquals("Idempotency-Key header is required and cannot be empty", exception.getMessage());
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Should throw exception when idempotency key is empty")
    void testInitiatePaymentEmptyIdempotencyKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> paymentService.initiatePayment(paymentRequest, "   "));

        assertEquals("Idempotency-Key header is required and cannot be empty", exception.getMessage());
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Should return existing transaction for duplicate idempotency key")
    void testInitiatePaymentDuplicateIdempotencyKey() {
        when(transactionService.findByIdempotencyKey("idempotency-key-123"))
            .thenReturn(Optional.of(testTransaction));
        PaymentInitiationResponse response = paymentService.initiatePayment(
            paymentRequest, "idempotency-key-123");
        assertNotNull(response);
        assertEquals("1", response.getTransactionId());
        assertEquals("TXN-12345678", response.getTransactionReference());
        verify(transactionService).findByIdempotencyKey("idempotency-key-123");
        verify(transactionService, never()).createTransaction(any());
    }

    @Test
    @DisplayName("Should successfully initiate credit card payment")
    void testInitiatePaymentCreditCardSuccess() throws StripeException {
        when(transactionService.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        when(transactionService.createTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);
        when(stripeService.processCreditCardPayment(any(), anyString(), any(), anyString()))
            .thenReturn(paymentIntent);
        when(transactionService.updateTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);
        PaymentInitiationResponse response = paymentService.initiatePayment(
            paymentRequest, "idempotency-key-123");
        assertNotNull(response);
        assertEquals("1", response.getTransactionId());
        verify(transactionService).createTransaction(any(Transaction.class));
        verify(stripeService).processCreditCardPayment(any(), anyString(), any(), anyString());
        verify(transactionService).updateTransaction(any(Transaction.class));
    }

    @Test
    @DisplayName("Should successfully initiate bank transfer payment")
    void testInitiatePaymentBankTransferSuccess() throws StripeException {
        paymentRequest.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        paymentRequest.setDetails(bankTransferDetails);
        testTransaction.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        when(transactionService.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        when(transactionService.createTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);
        when(stripeService.processBankTransferPayment(any(), anyString(), any(), anyString()))
            .thenReturn(paymentIntent);
        when(transactionService.updateTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);
        PaymentInitiationResponse response = paymentService.initiatePayment(
            paymentRequest, "idempotency-key-456");
        assertNotNull(response);
        verify(transactionService).createTransaction(any(Transaction.class));
        verify(stripeService).processBankTransferPayment(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Should handle Stripe exception during credit card payment")
    void testInitiatePaymentCreditCardStripeException() throws StripeException {
        when(transactionService.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        when(transactionService.createTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);

        StripeException stripeException = new StripeException("Card declined", "request-123", "card_declined", 402) {};
        when(stripeService.processCreditCardPayment(any(), anyString(), any(), anyString()))
            .thenThrow(stripeException);

        when(transactionService.updateTransaction(any(Transaction.class)))
            .thenReturn(testTransaction);

        PaymentInitiationResponse response = paymentService.initiatePayment(
            paymentRequest, "idempotency-key-789");

        assertNotNull(response);
        verify(transactionService).updateTransaction(any(Transaction.class));
    }

    @Test
    @DisplayName("Should get payment by ID")
    void testGetPaymentById() {
        when(transactionService.findById(1L))
            .thenReturn(Optional.of(testTransaction));
        PaymentInitiationResponse response = paymentService.getPaymentById(1L);
        assertNotNull(response);
        assertEquals("1", response.getTransactionId());
        assertEquals("TXN-12345678", response.getTransactionReference());
        verify(transactionService).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when payment not found by ID")
    void testGetPaymentByIdNotFound() {
        when(transactionService.findById(999L))
            .thenReturn(Optional.empty());
        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
            () -> paymentService.getPaymentById(999L));

        assertEquals("Payment not found with id: 999", exception.getMessage());
        verify(transactionService).findById(999L);
    }

    @Test
    @DisplayName("Should get all payments without filters")
    void testGetAllPaymentsNoFilters() {
        Transaction transaction2 = new Transaction();
        transaction2.setId(2L);
        transaction2.setAmount(new BigDecimal("200.00"));
        transaction2.setCurrency("eur");
        transaction2.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        transaction2.setStatus(PaymentStatus.COMPLETED);
        transaction2.setProvider("STRIPE");
        transaction2.setTransactionReference("TXN-87654321");
        transaction2.setCreatedAt(Instant.now());
        transaction2.setUpdatedAt(Instant.now());

        List<Transaction> transactions = Arrays.asList(testTransaction, transaction2);
        when(transactionService.findAll()).thenReturn(transactions);
        List<PaymentInitiationResponse> responses = paymentService.getAllPayments(
            null, null, null, null, null);
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(transactionService).findAll();
    }

    @Test
    @DisplayName("Should filter payments by status")
    void testGetAllPaymentsFilterByStatus() {
        Transaction completedTransaction = new Transaction();
        completedTransaction.setId(2L);
        completedTransaction.setAmount(new BigDecimal("200.00"));
        completedTransaction.setCurrency("eur");
        completedTransaction.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        completedTransaction.setStatus(PaymentStatus.COMPLETED);
        completedTransaction.setProvider("STRIPE");
        completedTransaction.setTransactionReference("TXN-87654321");
        completedTransaction.setCreatedAt(Instant.now());
        completedTransaction.setUpdatedAt(Instant.now());

        List<Transaction> transactions = Arrays.asList(testTransaction, completedTransaction);
        when(transactionService.findAll()).thenReturn(transactions);

        List<PaymentInitiationResponse> responses = paymentService.getAllPayments(
            PaymentStatus.COMPLETED, null, null, null, null);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("COMPLETED", responses.getFirst().getStatus());
    }

    @Test
    @DisplayName("Should filter payments by amount range")
    void testGetAllPaymentsFilterByAmountRange() {
        Transaction transaction2 = new Transaction();
        transaction2.setId(2L);
        transaction2.setAmount(new BigDecimal("500.00"));
        transaction2.setCurrency("eur");
        transaction2.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        transaction2.setStatus(PaymentStatus.COMPLETED);
        transaction2.setProvider("STRIPE");
        transaction2.setTransactionReference("TXN-87654321");
        transaction2.setCreatedAt(Instant.now());
        transaction2.setUpdatedAt(Instant.now());

        List<Transaction> transactions = Arrays.asList(testTransaction, transaction2);
        when(transactionService.findAll()).thenReturn(transactions);
        List<PaymentInitiationResponse> responses = paymentService.getAllPayments(
            null, null, null, new BigDecimal("50.00"), new BigDecimal("150.00"));
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(new BigDecimal("100.00"), responses.getFirst().getAmount());
    }

    @Test
    @DisplayName("Should throw exception for invalid start date format")
    void testGetAllPaymentsInvalidStartDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> paymentService.getAllPayments(null, "invalid-date", null, null, null));

        assertTrue(exception.getMessage().contains("Invalid startDate format"));
    }

    @Test
    @DisplayName("Should throw exception for invalid end date format")
    void testGetAllPaymentsInvalidEndDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> paymentService.getAllPayments(null, null, "invalid-date", null, null));

        assertTrue(exception.getMessage().contains("Invalid endDate format"));
    }

    @Test
    @DisplayName("Should filter payments by date range")
    void testGetAllPaymentsFilterByDateRange() {
        Instant yesterday = Instant.now().minusSeconds(86400);

        testTransaction.setCreatedAt(Instant.now());

        Transaction oldTransaction = new Transaction();
        oldTransaction.setId(2L);
        oldTransaction.setAmount(new BigDecimal("200.00"));
        oldTransaction.setCurrency("eur");
        oldTransaction.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        oldTransaction.setStatus(PaymentStatus.COMPLETED);
        oldTransaction.setProvider("STRIPE");
        oldTransaction.setTransactionReference("TXN-87654321");
        oldTransaction.setCreatedAt(yesterday.minusSeconds(86400));
        oldTransaction.setUpdatedAt(yesterday.minusSeconds(86400));

        List<Transaction> transactions = Arrays.asList(testTransaction, oldTransaction);
        when(transactionService.findAll()).thenReturn(transactions);

        LocalDate today = LocalDate.now();

        List<PaymentInitiationResponse> responses = paymentService.getAllPayments(
            null, today.toString(), today.toString(), null, null);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }
}

