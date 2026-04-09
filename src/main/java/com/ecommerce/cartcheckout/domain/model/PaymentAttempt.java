package com.ecommerce.cartcheckout.domain.model;

import com.ecommerce.cartcheckout.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts",
       indexes = @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private String providerReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    public static PaymentAttempt create(Order order, String idempotencyKey) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.order = order;
        attempt.status = PaymentStatus.PENDING;
        attempt.amount = order.getTotalAmount();
        attempt.idempotencyKey = idempotencyKey;
        attempt.createdAt = Instant.now();
        attempt.updatedAt = Instant.now();
        return attempt;
    }

    public void confirm(String providerReference) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot confirm payment in status: " + this.status);
        }
        this.status = PaymentStatus.CONFIRMED;
        this.providerReference = providerReference;
        this.updatedAt = Instant.now();
    }


    public void fail(String providerReference) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot fail payment in status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.providerReference = providerReference;
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public boolean isTerminal() {
        return this.status == PaymentStatus.CONFIRMED || this.status == PaymentStatus.FAILED;
    }
}
