package com.workshop.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.workshop.orders.lambda.ErrorCode;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the {@link APIGatewayProxyResponseEvent} that API Gateway turns into the HTTP response,
 * and owns the JSON configuration used for HTTP bodies.
 */
final class HttpResponses {

    /**
     * Strict about types ({@code "quantity": 1.5} or {@code "2"} is rejected rather than silently
     * coerced) but tolerant of extra fields, so a client sending {@code "id"} is not an error.
     */
    static final ObjectMapper JSON = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private HttpResponses() {
    }

    static APIGatewayProxyResponseEvent ok(Object body) {
        return json(200, body);
    }

    static APIGatewayProxyResponseEvent created(Object body, String location) {
        APIGatewayProxyResponseEvent response = json(201, body);
        response.getHeaders().put("Location", location);
        return response;
    }

    static APIGatewayProxyResponseEvent noContent() {
        return new APIGatewayProxyResponseEvent().withStatusCode(204).withHeaders(new HashMap<>());
    }

    static APIGatewayProxyResponseEvent error(int status, ErrorCode code, String message) {
        return json(status, new ErrorResponse(code.name(), message));
    }

    static APIGatewayProxyResponseEvent methodNotAllowed(String method, String allowed) {
        APIGatewayProxyResponseEvent response =
                error(405, ErrorCode.METHOD_NOT_ALLOWED, "Method " + method + " is not allowed on this resource");
        response.getHeaders().put("Allow", allowed);
        return response;
    }

    private static APIGatewayProxyResponseEvent json(int status, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(headers)
                .withBody(toJson(body));
    }

    static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
