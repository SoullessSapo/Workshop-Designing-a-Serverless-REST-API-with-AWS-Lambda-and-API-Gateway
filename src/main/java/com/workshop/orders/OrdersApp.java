package com.workshop.orders;

import com.workshop.orders.application.OrderService;
import com.workshop.orders.persistence.InMemoryOrderRepository;
import com.workshop.orders.persistence.SampleOrders;

/**
 * Composition root: wires the application with its default (in-memory, pre-seeded) storage.
 * Lambda handlers call this from their no-arg constructor; tests build their own instances.
 */
public final class OrdersApp {

    private OrdersApp() {
    }

    public static OrderService createOrderService() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        SampleOrders.loadInto(repository);
        return new OrderService(repository);
    }
}
