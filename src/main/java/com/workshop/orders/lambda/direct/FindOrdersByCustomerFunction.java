package com.workshop.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.workshop.orders.OrdersApp;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.Order;

import java.util.List;

/**
 * {@code GET /orders?customerId=...} in Architecture A.
 */
public class FindOrdersByCustomerFunction implements RequestHandler<CustomerOrdersQuery, List<Order>> {

    private final OrderService orders;

    public FindOrdersByCustomerFunction() {
        this(OrdersApp.createOrderService());
    }

    public FindOrdersByCustomerFunction(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public List<Order> handleRequest(CustomerOrdersQuery input, Context context) {
        String customerId = input == null ? null : input.getCustomerId();
        return GatewayMappedException.translatingErrors(() -> orders.getOrdersByCustomer(customerId));
    }
}
