package com.workshop.orders.application;

import com.workshop.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * Port to wherever orders are stored. The application only depends on this interface, so the
 * in-memory implementation can later be swapped for DynamoDB without touching {@link OrderService}.
 */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(String id);

    /** Orders of the given customer, in the order they were stored. */
    List<Order> findByCustomerId(String customerId);

    /** Removes the order; returns {@code false} if there was no order with that id. */
    boolean deleteById(String id);
}
