package com.workshop.orders.persistence;

import com.workshop.orders.application.OrderRepository;
import com.workshop.orders.domain.Order;

import java.util.List;

/**
 * Predefined orders loaded into every fresh execution environment, so reads have something to
 * return even though nothing is persisted. CUST-100 deliberately has no orders.
 */
public final class SampleOrders {

    public static final List<Order> ORDERS = List.of(
            new Order("ORD-001", "CUST-42", "MacBook Air", 1),
            new Order("ORD-002", "CUST-42", "USB-C Hub", 2),
            new Order("ORD-42", "CUST-7", "Keyboard", 2));

    private SampleOrders() {
    }

    public static void loadInto(OrderRepository repository) {
        ORDERS.forEach(repository::save);
    }
}
