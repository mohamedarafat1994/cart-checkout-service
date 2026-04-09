package com.ecommerce.cartcheckout.service;

import com.ecommerce.cartcheckout.domain.exception.DomainExceptions.*;
import com.ecommerce.cartcheckout.domain.model.Cart;
import com.ecommerce.cartcheckout.domain.model.Order;
import com.ecommerce.cartcheckout.repository.CartRepository;
import com.ecommerce.cartcheckout.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;

   
    @Transactional
    public Order checkout(UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId.toString()));

        var existingOrder = orderRepository.findByCartId(cartId);
        if (existingOrder.isPresent()) {
            log.warn("Cart {} already checked out. Returning existing order {}", cartId, existingOrder.get().getId());
            throw new DuplicateCheckoutException(cartId.toString());
        }

        cart.checkout();
        cartRepository.save(cart);

        Order order = Order.createFromCart(cart);
        order = orderRepository.save(order);

        log.info("Order created: {} from cart: {} | total: {}",
                order.getId(), cartId, order.getTotalAmount());
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }
}
