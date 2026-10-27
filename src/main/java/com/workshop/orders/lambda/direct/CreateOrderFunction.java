package com.workshop.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.workshop.orders.OrdersApp;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;

/**
 * {@code POST /orders} in Architecture A, and the function invoked directly in Part II.
 * Lambda turns the input JSON into a {@link CreateOrderRequest} and the returned {@link Order} back into JSON.
 */
public class CreateOrderFunction implements RequestHandler<CreateOrderRequest, Order> {

    private final OrderService orders;

    public CreateOrderFunction() {
        this(OrdersApp.createOrderService());
    }

    public CreateOrderFunction(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public Order handleRequest(CreateOrderRequest input, Context context) {
        return GatewayMappedException.translatingErrors(() -> orders.createOrder(input));
    }
}
