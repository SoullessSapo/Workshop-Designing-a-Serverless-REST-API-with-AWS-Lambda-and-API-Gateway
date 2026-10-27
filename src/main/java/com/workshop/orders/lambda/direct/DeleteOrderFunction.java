package com.workshop.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.workshop.orders.OrdersApp;
import com.workshop.orders.application.OrderService;

/**
 * {@code DELETE /orders/{id}} in Architecture A. Returns nothing on success (the runtime emits
 * {@code null}); API Gateway turns that into {@code 204 No Content}, and an {@code ORDER_NOT_FOUND}
 * error into {@code 404}.
 */
public class DeleteOrderFunction implements RequestHandler<OrderIdInput, Void> {

    private final OrderService orders;

    public DeleteOrderFunction() {
        this(OrdersApp.createOrderService());
    }

    public DeleteOrderFunction(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public Void handleRequest(OrderIdInput input, Context context) {
        String id = input == null ? null : input.getId();
        return GatewayMappedException.translatingErrors(() -> {
            orders.deleteOrder(id);
            return null;
        });
    }
}
