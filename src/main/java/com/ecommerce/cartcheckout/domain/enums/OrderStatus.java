package com.ecommerce.cartcheckout.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    CREATED {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return EnumSet.of(PENDING_PAYMENT, CANCELLED);
        }
    },

    PENDING_PAYMENT {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return EnumSet.of(PAID, PAYMENT_FAILED);
        }
    },

    PAYMENT_FAILED {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return EnumSet.of(PENDING_PAYMENT, CANCELLED);
        }
    },

    PAID {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return EnumSet.noneOf(OrderStatus.class); // Terminal state
        }
    },

    CANCELLED {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return EnumSet.noneOf(OrderStatus.class); // Terminal state
        }
    };

    public abstract Set<OrderStatus> allowedTransitions();

  
    public void validateTransitionTo(OrderStatus target) {
        if (!allowedTransitions().contains(target)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s → %s. Allowed: %s",
                    this, target, allowedTransitions())
            );
        }
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
