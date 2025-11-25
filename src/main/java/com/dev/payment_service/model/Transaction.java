package com.dev.payment_service.model;

import com.dev.payment_service.enums.PaymentMethod;
import com.dev.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transactions_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    private String currency;

    @Column(name = "provider_reference_id", length = 100)
    private String providerReferenceId;

    @Column(nullable = false, length = 50)
    private String provider;

    // TODO: Replace with user from Spring Security Authentication
    @Column(name = "created_by")
    private String createdBy;

    // TODO: Replace with user from Spring Security Authentication
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;

        if (this.createdBy == null) {
            this.createdBy = "STATIC_USER";   // TODO: replace with authenticated user
        }
        if (this.lastModifiedBy == null) {
            this.lastModifiedBy = this.createdBy;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();

        if (this.lastModifiedBy == null) {
            this.lastModifiedBy = "STATIC_USER";  // TODO: replace with authenticated user
        }
    }


    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

}
