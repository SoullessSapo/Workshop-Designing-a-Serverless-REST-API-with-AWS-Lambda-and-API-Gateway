package com.workshop.orders.persistence;

import com.workshop.orders.application.OrderRepository;
import com.workshop.orders.domain.Order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps orders in a map inside the JVM.
 *
 * <p>On Lambda this lives exactly as long as one execution environment: it survives between warm
 * invocations of the same environment, is not shared between concurrent environments or between
 * different functions, and is lost when the environment is recycled. It is a stand-in, not storage.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> orders = new LinkedHashMap<>();

    @Override
    public synchronized void save(Order order) {
        orders.put(order.getId(), order);
    }

    @Override
    public synchronized Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public synchronized List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(order -> order.getCustomerId().equals(customerId))
                .toList();
    }

    @Override
    public synchronized boolean deleteById(String id) {
        return orders.remove(id) != null;
    }
}
