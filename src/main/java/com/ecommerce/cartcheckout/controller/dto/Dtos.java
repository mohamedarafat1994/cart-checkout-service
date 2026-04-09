package com.ecommerce.cartcheckout.controller.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Dtos {

    // ── Cart DTOs ──

    @Data @Builder
    public static class AddItemRequest {
        @NotBlank(message = "productId is required")
        private String productId;

        @Min(value = 1, message = "quantity must be at least 1")
        private int quantity;

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be positive")
        private BigDecimal price;
    }

    @Data @Builder
    public static class CartResponse {
        private UUID id;
        private String status;
        private List<CartItemResponse> items;
        private BigDecimal totalPrice;
        private Instant createdAt;
    }

    @Data @Builder
    public static class CartItemResponse {
        private UUID id;
        private String productId;
        private int quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }

    // ── Order DTOs ──

    @Data @Builder
    public static class OrderResponse {
        private UUID id;
        private UUID cartId;
        private String status;
        private BigDecimal totalAmount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ── Payment DTOs ──

    @Data @Builder
    public static class PaymentStartResponse {
        private UUID paymentAttemptId;
        private UUID orderId;
        private String idempotencyKey;
        private String status;
        private BigDecimal amount;
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WebhookRequest {
        @NotNull
        private UUID paymentAttemptId;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String result; // "CONFIRMED" or "FAILED"

        private String providerReference;
    }

    @Data @Builder
    public static class WebhookResponse {
        private String status;
        private String message;
    }

    // ── Error DTO ──

    @Data @Builder
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private Instant timestamp;
    }
}
