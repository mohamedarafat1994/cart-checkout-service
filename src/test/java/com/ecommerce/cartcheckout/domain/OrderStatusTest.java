package com.ecommerce.cartcheckout.domain;

import com.ecommerce.cartcheckout.domain.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderStatus State Machine Tests")
class OrderStatusTest {

    @Nested
    @DisplayName("Valid Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("CREATED → PENDING_PAYMENT is allowed")
        void createdToPendingPayment() {
            assertDoesNotThrow(() ->
                    OrderStatus.CREATED.validateTransitionTo(OrderStatus.PENDING_PAYMENT));
        }

        @Test
        @DisplayName("CREATED → CANCELLED is allowed")
        void createdToCancelled() {
            assertDoesNotThrow(() ->
                    OrderStatus.CREATED.validateTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAID is allowed")
        void pendingPaymentToPaid() {
            assertDoesNotThrow(() ->
                    OrderStatus.PENDING_PAYMENT.validateTransitionTo(OrderStatus.PAID));
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAYMENT_FAILED is allowed")
        void pendingPaymentToFailed() {
            assertDoesNotThrow(() ->
                    OrderStatus.PENDING_PAYMENT.validateTransitionTo(OrderStatus.PAYMENT_FAILED));
        }

        @Test
        @DisplayName("PAYMENT_FAILED → PENDING_PAYMENT is allowed (retry)")
        void paymentFailedToPendingPayment() {
            assertDoesNotThrow(() ->
                    OrderStatus.PAYMENT_FAILED.validateTransitionTo(OrderStatus.PENDING_PAYMENT));
        }

        @Test
        @DisplayName("PAYMENT_FAILED → CANCELLED is allowed")
        void paymentFailedToCancelled() {
            assertDoesNotThrow(() ->
                    OrderStatus.PAYMENT_FAILED.validateTransitionTo(OrderStatus.CANCELLED));
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("CREATED → PAID is NOT allowed")
        void createdToPaid() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    OrderStatus.CREATED.validateTransitionTo(OrderStatus.PAID));
            assertTrue(ex.getMessage().contains("Invalid state transition"));
        }

        @Test
        @DisplayName("PAID → any state is NOT allowed (terminal)")
        void paidIsTerminal() {
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.PAID.validateTransitionTo(OrderStatus.CREATED));
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.PAID.validateTransitionTo(OrderStatus.PENDING_PAYMENT));
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.PAID.validateTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("CANCELLED → any state is NOT allowed (terminal)")
        void cancelledIsTerminal() {
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.CANCELLED.validateTransitionTo(OrderStatus.CREATED));
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.CANCELLED.validateTransitionTo(OrderStatus.PENDING_PAYMENT));
        }

        @Test
        @DisplayName("PENDING_PAYMENT → CREATED is NOT allowed")
        void pendingPaymentToCreated() {
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.PENDING_PAYMENT.validateTransitionTo(OrderStatus.CREATED));
        }

        @Test
        @DisplayName("PAYMENT_FAILED → PAID is NOT allowed (must retry)")
        void paymentFailedToPaid() {
            assertThrows(IllegalStateException.class, () ->
                    OrderStatus.PAYMENT_FAILED.validateTransitionTo(OrderStatus.PAID));
        }
    }

    @Nested
    @DisplayName("Terminal State Detection")
    class TerminalStates {

        @Test
        void paidIsTerminal() {
            assertTrue(OrderStatus.PAID.isTerminal());
        }

        @Test
        void cancelledIsTerminal() {
            assertTrue(OrderStatus.CANCELLED.isTerminal());
        }

        @Test
        void createdIsNotTerminal() {
            assertFalse(OrderStatus.CREATED.isTerminal());
        }

        @Test
        void pendingPaymentIsNotTerminal() {
            assertFalse(OrderStatus.PENDING_PAYMENT.isTerminal());
        }

        @Test
        void paymentFailedIsNotTerminal() {
            assertFalse(OrderStatus.PAYMENT_FAILED.isTerminal());
        }
    }
}
