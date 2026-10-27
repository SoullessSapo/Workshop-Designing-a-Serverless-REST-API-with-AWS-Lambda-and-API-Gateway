package com.workshop.orders.lambda.direct;

/**
 * Lambda input built by the API Gateway mapping template from the {@code {id}} path parameter:
 * {@code {"id": "ORD-42"}}.
 */
public class OrderIdInput {

    private String id;

    public OrderIdInput() {
    }

    public OrderIdInput(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
