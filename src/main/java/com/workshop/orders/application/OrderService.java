package com.workshop.orders.application;

import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.InvalidOrderRequestException;
import com.workshop.orders.domain.Order;
import com.workshop.orders.domain.OrderNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Orders use cases. Plain Java: it knows nothing about HTTP, API Gateway or Lambda.
 * Failures are reported with domain exceptions; adapters decide how to present them.
 */
public class OrderService {

    private final OrderRepository repository;
    private final Supplier<String> idGenerator;

    public OrderService(OrderRepository repository) {
        this(repository, OrderService::randomOrderId);
    }

    public OrderService(OrderRepository repository, Supplier<String> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public Order createOrder(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderRequestException("order data is required");
        }
        List<String> violations = new ArrayList<>();
        String customerId = trimToNull(request.getCustomerId());
        String product = trimToNull(request.getProduct());
        Integer quantity = request.getQuantity();
        if (customerId == null) {
            violations.add("customerId is required");
        }
        if (product == null) {
            violations.add("product is required");
        }
        if (quantity == null) {
            violations.add("quantity is required");
        } else if (quantity < 1) {
            violations.add("quantity must be greater than 0");
        }
        if (!violations.isEmpty()) {
            throw new InvalidOrderRequestException(violations);
        }

        Order order = new Order(idGenerator.get(), customerId, product, quantity);
        repository.save(order);
        return order;
    }

    public Order getOrder(String id) {
        String orderId = requireText(id, "order id is required");
        return repository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Returns an empty list (not an error) when the customer has no orders. */
    public List<Order> getOrdersByCustomer(String customerId) {
        return repository.findByCustomerId(requireText(customerId, "customerId is required"));
    }

    /** Deleting an order that does not exist is an error, so callers can tell the two outcomes apart. */
    public void deleteOrder(String id) {
        String orderId = requireText(id, "order id is required");
        if (!repository.deleteById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
    }

    static String randomOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String requireText(String value, String violation) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new InvalidOrderRequestException(violation);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
