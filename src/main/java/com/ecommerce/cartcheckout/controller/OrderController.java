package com.ecommerce.cartcheckout.controller;

import com.ecommerce.cartcheckout.controller.dto.Dtos;
import com.ecommerce.cartcheckout.domain.model.Order;
import com.ecommerce.cartcheckout.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/carts/{cartId}/checkout")
    public ResponseEntity<Dtos.OrderResponse> checkout(@PathVariable UUID cartId) {
        Order order = orderService.checkout(cartId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Dtos.OrderResponse> getOrder(@PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(toResponse(order));
    }

    private Dtos.OrderResponse toResponse(Order order) {
        return Dtos.OrderResponse.builder()
                .id(order.getId())
                .cartId(order.getCart().getId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
