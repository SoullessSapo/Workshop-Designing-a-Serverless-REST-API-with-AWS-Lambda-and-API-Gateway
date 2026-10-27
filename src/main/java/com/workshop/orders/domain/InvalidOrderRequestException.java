package com.workshop.orders.domain;

import java.util.List;

/**
 * Thrown when input supplied to the application breaks one or more business rules.
 * The message lists every violation, so a client can fix all of them in one round trip.
 */
public class InvalidOrderRequestException extends RuntimeException {

    private final List<String> violations;

    public InvalidOrderRequestException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public InvalidOrderRequestException(String violation) {
        this(List.of(violation));
    }

    public List<String> getViolations() {
        return violations;
    }
}
