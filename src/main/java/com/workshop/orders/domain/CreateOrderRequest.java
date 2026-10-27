package com.workshop.orders.domain;

/**
 * The data a client supplies to place an order. The server assigns the id.
 *
 * <p>Written as a JavaBean (no-arg constructor + setters) because it is used directly as Lambda input:
 * the runtime's serializer does not auto-detect constructors of ordinary classes. Records do work
 * with the serializer version tested locally (see {@code LambdaRuntimeSerializationTest}), but a
 * bean works with every runtime version. {@code quantity} is an {@link Integer} so that "missing"
 * can be told apart from "zero".
 */
public class CreateOrderRequest {

    private String customerId;
    private String product;
    private Integer quantity;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String customerId, String product, Integer quantity) {
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
