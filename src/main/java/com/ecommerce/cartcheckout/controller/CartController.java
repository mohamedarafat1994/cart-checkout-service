package com.ecommerce.cartcheckout.controller;

import com.ecommerce.cartcheckout.controller.dto.Dtos;
import com.ecommerce.cartcheckout.domain.model.Cart;
import com.ecommerce.cartcheckout.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public ResponseEntity<Dtos.CartResponse> createCart() {
        Cart cart = cartService.createCart();
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(cart));
    }

    @PostMapping("/{cartId}/items")
    public ResponseEntity<Dtos.CartResponse> addItem(
            @PathVariable UUID cartId,
            @Valid @RequestBody Dtos.AddItemRequest request) {
        Cart cart = cartService.addItem(cartId, request.getProductId(),
                request.getQuantity(), request.getPrice());
        return ResponseEntity.ok(toResponse(cart));
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Dtos.CartResponse> getCart(@PathVariable UUID cartId) {
        Cart cart = cartService.getCart(cartId);
        return ResponseEntity.ok(toResponse(cart));
    }

    private Dtos.CartResponse toResponse(Cart cart) {
        return Dtos.CartResponse.builder()
                .id(cart.getId())
                .status(cart.getStatus().name())
                .items(cart.getItems().stream()
                        .map(item -> Dtos.CartItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .subtotal(item.getSubtotal())
                                .build())
                        .collect(Collectors.toList()))
                .totalPrice(cart.getTotalPrice())
                .createdAt(cart.getCreatedAt())
                .build();
    }
}
