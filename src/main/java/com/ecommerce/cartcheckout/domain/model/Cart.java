package com.ecommerce.cartcheckout.domain.model;

import com.ecommerce.cartcheckout.domain.enums.CartStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    public static Cart create() {
        Cart cart = new Cart();
        cart.status = CartStatus.ACTIVE;
        cart.createdAt = Instant.now();
        cart.updatedAt = Instant.now();
        return cart;
    }

    public CartItem addItem(String productId, int quantity, BigDecimal price) {
        if (status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cannot modify a checked-out cart");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }

        CartItem item = CartItem.create(this, productId, quantity, price);
        items.add(item);
        this.updatedAt = Instant.now();
        return item;
    }

    public void checkout() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }
        if (status == CartStatus.CHECKED_OUT) {
            throw new IllegalStateException("Cart is already checked out");
        }
        this.status = CartStatus.CHECKED_OUT;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getTotalPrice() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isActive() {
        return status == CartStatus.ACTIVE;
    }
}
