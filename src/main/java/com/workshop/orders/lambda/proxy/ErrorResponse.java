package com.workshop.orders.lambda.proxy;

/**
 * The API's error document: {@code {"error": "ORDER_NOT_FOUND", "message": "Order ORD-999 does not exist"}}.
 */
public record ErrorResponse(String error, String message) {
}
