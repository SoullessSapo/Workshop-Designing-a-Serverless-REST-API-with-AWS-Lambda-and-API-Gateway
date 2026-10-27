package com.workshop.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.workshop.orders.OrdersApp;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.Order;

/**
 * {@code GET /orders/{id}} in Architecture A.
 */
public class GetOrderFunction implements RequestHandler<OrderIdInput, Order> {

    private final OrderService orders;

    public GetOrderFunction() {
        this(OrdersApp.createOrderService());
    }

    public GetOrderFunction(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public Order handleRequest(OrderIdInput input, Context context) {
        String id = input == null ? null : input.getId();
        return GatewayMappedException.translatingErrors(() -> orders.getOrder(id));
    }
}
