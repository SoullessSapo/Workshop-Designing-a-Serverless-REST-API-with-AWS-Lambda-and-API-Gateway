package com.workshop.orders;

import com.workshop.orders.application.OrderRepository;
import com.workshop.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/** Simulates an unexpected infrastructure failure, to exercise the 500 paths. */
public class FailingOrderRepository implements OrderRepository {

    public static final String SECRET_DETAIL = "connection refused: db-internal.local:5432";

    @Override
    public void save(Order order) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public Optional<Order> findById(String id) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public boolean deleteById(String id) {
        throw new IllegalStateException(SECRET_DETAIL);
    }
}
