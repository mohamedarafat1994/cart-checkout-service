package com.ecommerce.cartcheckout.service;

import com.ecommerce.cartcheckout.domain.enums.OrderStatus;
import com.ecommerce.cartcheckout.domain.exception.DomainExceptions.*;
import com.ecommerce.cartcheckout.domain.model.Order;
import com.ecommerce.cartcheckout.domain.model.PaymentAttempt;
import com.ecommerce.cartcheckout.repository.OrderRepository;
import com.ecommerce.cartcheckout.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final MockPaymentProvider mockPaymentProvider;

    /**
     * Starts a payment for an order.
     *
     * Guards:
     * 1. Order must be in CREATED or PAYMENT_FAILED state
     * 2. No active PENDING payment attempt must exist (prevents double-charge)
     * 3. Generates a unique idempotency key per attempt
     */
    @Transactional
    public PaymentAttempt startPayment(UUID orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        // Guard 1: Check order state allows payment
        if (!order.canStartPayment()) {
            throw new InvalidStateException(
                    "Cannot start payment for order in state: " + order.getStatus());
        }

        // Guard 2: Prevent duplicate active payments
        if (order.hasActivePendingPayment()) {
            throw new DuplicatePaymentException(
                    "Order " + orderId + " already has an active pending payment");
        }

        // Generate idempotency key: orderId + attempt count
        long attemptNumber = order.getPaymentAttempts().size() + 1;
        String idempotencyKey = orderId + "_attempt_" + attemptNumber;

        // Create payment attempt
        PaymentAttempt attempt = PaymentAttempt.create(order, idempotencyKey);
        attempt = paymentAttemptRepository.save(attempt);

        // Transition order to PENDING_PAYMENT
        order.transitionTo(OrderStatus.PENDING_PAYMENT);
        orderRepository.save(order);

        log.info("Payment started: orderId={}, attemptId={}, idempotencyKey={}, amount={}",
                orderId, attempt.getId(), idempotencyKey, attempt.getAmount());

        // Trigger mock payment provider asynchronously
        mockPaymentProvider.simulatePayment(attempt.getId(), idempotencyKey, attempt.getAmount());

        return attempt;
    }

    /**
     * Processes a payment webhook from the provider.
     *
     * Idempotency strategy:
     * 1. Look up payment attempt by idempotencyKey
     * 2. If attempt is already in a terminal state, return success (idempotent)
     * 3. If attempt is PENDING, process the result
     * 4. Uses pessimistic locking on the order to prevent race conditions
     */
    @Transactional
    public PaymentAttempt processWebhook(UUID paymentAttemptId, String idempotencyKey,
                                          String result, String providerReference) {
        // Find the payment attempt by idempotency key
        PaymentAttempt attempt = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Payment attempt not found for idempotency key: " + idempotencyKey));

        // Idempotency check: if already processed, return as-is
        if (attempt.isTerminal()) {
            log.warn("Duplicate webhook received for idempotencyKey={}. Already in state={}. Ignoring.",
                    idempotencyKey, attempt.getStatus());
            return attempt;
        }

        // Lock the order for safe state transition
        Order order = orderRepository.findByIdWithLock(attempt.getOrder().getId())
                .orElseThrow(() -> new OrderNotFoundException(
                        attempt.getOrder().getId().toString()));

        if ("CONFIRMED".equalsIgnoreCase(result)) {
            attempt.confirm(providerReference);
            order.transitionTo(OrderStatus.PAID);
            log.info("Payment CONFIRMED: orderId={}, attemptId={}, providerRef={}",
                    order.getId(), attempt.getId(), providerReference);
        } else if ("FAILED".equalsIgnoreCase(result)) {
            attempt.fail(providerReference);
            order.transitionTo(OrderStatus.PAYMENT_FAILED);
            log.info("Payment FAILED: orderId={}, attemptId={}, providerRef={}",
                    order.getId(), attempt.getId(), providerReference);
        } else {
            throw new InvalidStateException("Unknown payment result: " + result);
        }

        paymentAttemptRepository.save(attempt);
        orderRepository.save(order);

        return attempt;
    }
}
