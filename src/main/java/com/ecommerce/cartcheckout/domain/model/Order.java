package com.ecommerce.cartcheckout.domain.model;

import com.ecommerce.cartcheckout.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false, unique = true)
    private Cart cart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PaymentAttempt> paymentAttempts = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Optimistic locking to prevent concurrent state corruption.
     */
    @Version
    private Long version;

    /**
     * Factory method — creates an Order from a checked-out Cart.
     */
    public static Order createFromCart(Cart cart) {
        Order order = new Order();
        order.cart = cart;
        order.status = OrderStatus.CREATED;
        order.totalAmount = cart.getTotalPrice();
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        return order;
    }

    /**
     * Transitions the order to a new state, enforcing the state machine.
     * @throws IllegalStateException if the transition is not allowed
     */
    public void transitionTo(OrderStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if payment can be initiated for this order.
     */
    public boolean canStartPayment() {
        return status == OrderStatus.CREATED || status == OrderStatus.PAYMENT_FAILED;
    }

    /**
     * Checks if there is an active (PENDING) payment attempt.
     */
    public boolean hasActivePendingPayment() {
        return paymentAttempts.stream()
                .anyMatch(PaymentAttempt::isPending);
    }
}
