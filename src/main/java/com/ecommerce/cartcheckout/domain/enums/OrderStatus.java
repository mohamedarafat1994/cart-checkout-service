package com.ecommerce.cartcheckout.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order State Machine:
 * 
 *   CREATED ──────► PENDING_PAYMENT ──────► PAID
 *                        │                    
 *                        ▼                    
 *                   PAYMENT_FAILED ──────► PENDING_PAYMENT (retry)
 *                        │
 *                        ▼
 *                    CANCELLED
 * 
 * CREATED can also transition directly to CANCELLED.
 */
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

    /**
     * Returns the set of states this status can transition to.
     */
    public abstract Set<OrderStatus> allowedTransitions();

    /**
     * Validates whether a transition from this state to the target is allowed.
     * @throws IllegalStateException if transition is invalid
     */
    public void validateTransitionTo(OrderStatus target) {
        if (!allowedTransitions().contains(target)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s → %s. Allowed: %s",
                    this, target, allowedTransitions())
            );
        }
    }

    /**
     * Checks if this is a terminal (final) state.
     */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
