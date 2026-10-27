package com.workshop.orders.lambda;

/**
 * The machine-readable {@code error} values of the API's error document. Both integration styles
 * (mapping templates and proxy) emit exactly these, so clients see one contract.
 */
public enum ErrorCode {
    INVALID_REQUEST,
    ORDER_NOT_FOUND,
    ROUTE_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    INTERNAL_ERROR
}
