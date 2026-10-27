package com.workshop.orders.lambda.direct;

/**
 * Lambda input built by the API Gateway mapping template from the {@code customerId} query
 * parameter: {@code {"customerId": "CUST-42"}}.
 */
public class CustomerOrdersQuery {

    private String customerId;

    public CustomerOrdersQuery() {
    }

    public CustomerOrdersQuery(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
