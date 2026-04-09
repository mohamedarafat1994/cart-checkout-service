package com.ecommerce.cartcheckout.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class DomainExceptions {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class CartNotFoundException extends RuntimeException {
        public CartNotFoundException(String id) {
            super("Cart not found: " + id);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String id) {
            super("Order not found: " + id);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicatePaymentException extends RuntimeException {
        public DuplicatePaymentException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateCheckoutException extends RuntimeException {
        public DuplicateCheckoutException(String cartId) {
            super("Cart already checked out: " + cartId);
        }
    }
}
