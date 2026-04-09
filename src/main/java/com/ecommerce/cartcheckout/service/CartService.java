package com.ecommerce.cartcheckout.service;

import com.ecommerce.cartcheckout.domain.exception.DomainExceptions.*;
import com.ecommerce.cartcheckout.domain.model.Cart;
import com.ecommerce.cartcheckout.domain.model.CartItem;
import com.ecommerce.cartcheckout.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;

    @Transactional
    public Cart createCart() {
        Cart cart = Cart.create();
        cart = cartRepository.save(cart);
        log.info("Cart created: {}", cart.getId());
        return cart;
    }

    @Transactional
    public Cart addItem(UUID cartId, String productId, int quantity, BigDecimal price) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId.toString()));

        CartItem item = cart.addItem(productId, quantity, price);
        cart = cartRepository.save(cart);
        log.info("Item added to cart {}: productId={}, qty={}, price={}",
                cartId, productId, quantity, price);
        return cart;
    }

    @Transactional(readOnly = true)
    public Cart getCart(UUID cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId.toString()));
    }
}
