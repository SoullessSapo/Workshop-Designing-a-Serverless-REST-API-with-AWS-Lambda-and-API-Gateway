package com.workshop.orders.lambda.direct;

import com.workshop.orders.domain.InvalidOrderRequestException;
import com.workshop.orders.domain.OrderNotFoundException;
import com.workshop.orders.lambda.ErrorCode;

import java.util.function.Supplier;

/**
 * Architecture A (non-proxy integration) error contract.
 *
 * <p>When a Lambda function throws, the runtime returns {@code {"errorMessage": ..., "errorType": ...}}
 * and API Gateway picks an integration response by matching a regex against {@code errorMessage}.
 * This exception prefixes the message with an {@link ErrorCode} ({@code "ORDER_NOT_FOUND: Order X does
 * not exist"}) so the gateway can select 404/400/500 without the Java code knowing any status code.
 * The selection patterns live in {@code infra/template.yaml}.
 */
public class GatewayMappedException extends RuntimeException {

    public static final String SEPARATOR = ": ";

    public GatewayMappedException(ErrorCode code, String message, Throwable cause) {
        super(code.name() + SEPARATOR + message, cause);
    }

    /** Runs a use case and converts domain failures into prefixed errors the gateway can match. */
    static <T> T translatingErrors(Supplier<T> useCase) {
        try {
            return useCase.get();
        } catch (InvalidOrderRequestException e) {
            throw new GatewayMappedException(ErrorCode.INVALID_REQUEST, e.getMessage(), e);
        } catch (OrderNotFoundException e) {
            throw new GatewayMappedException(ErrorCode.ORDER_NOT_FOUND, e.getMessage(), e);
        } catch (RuntimeException e) {
            // The gateway answers 500 with a generic body; the real cause goes to CloudWatch Logs.
            e.printStackTrace();
            throw new GatewayMappedException(ErrorCode.INTERNAL_ERROR, "Unexpected error", e);
        }
    }
}
