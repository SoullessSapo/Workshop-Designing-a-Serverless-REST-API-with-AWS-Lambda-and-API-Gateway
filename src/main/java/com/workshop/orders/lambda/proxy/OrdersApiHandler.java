package com.workshop.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.workshop.orders.OrdersApp;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.InvalidOrderRequestException;
import com.workshop.orders.domain.Order;
import com.workshop.orders.domain.OrderNotFoundException;
import com.workshop.orders.lambda.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Architecture B: one Lambda behind a Lambda proxy integration for the whole Orders API.
 *
 * <p>This is the HTTP adapter. It is the only place that knows about methods, paths, parameters,
 * headers, status codes and JSON bodies. It translates an API Gateway proxy event into a call to
 * {@link OrderService} and translates the result (or domain exception) back into an HTTP response.
 */
public class OrdersApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    static final String ORDERS = "/orders";
    static final String ORDER = "/orders/{id}";

    private final OrderService orders;
    private final boolean logEvents;

    public OrdersApiHandler() {
        this(OrdersApp.createOrderService(), "true".equalsIgnoreCase(System.getenv("LOG_EVENTS")));
    }

    public OrdersApiHandler(OrderService orders) {
        this(orders, false);
    }

    OrdersApiHandler(OrderService orders, boolean logEvents) {
        this.orders = orders;
        this.logEvents = logEvents;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            if (logEvents) {
                log(context, "event: " + HttpResponses.toJson(event));
            }
            return route(event);
        } catch (InvalidOrderRequestException e) {
            return HttpResponses.error(400, ErrorCode.INVALID_REQUEST, e.getMessage());
        } catch (OrderNotFoundException e) {
            return HttpResponses.error(404, ErrorCode.ORDER_NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            // Never leak internals to the client; the stack trace goes to CloudWatch Logs.
            String reference = context == null ? "n/a" : context.getAwsRequestId();
            log(context, "Unhandled error (request " + reference + "): " + e);
            e.printStackTrace();
            return HttpResponses.error(500, ErrorCode.INTERNAL_ERROR, "Unexpected error. Reference: " + reference);
        }
    }

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) {
        String resource = event.getResource() == null ? "" : event.getResource();
        String method = event.getHttpMethod() == null ? "" : event.getHttpMethod();
        return switch (resource) {
            case ORDERS -> switch (method) {
                case "POST" -> createOrder(event);
                case "GET" -> findOrders(event);
                default -> HttpResponses.methodNotAllowed(method, "GET, POST");
            };
            case ORDER -> switch (method) {
                case "GET" -> getOrder(event);
                case "DELETE" -> deleteOrder(event);
                default -> HttpResponses.methodNotAllowed(method, "GET, DELETE");
            };
            default -> HttpResponses.error(404, ErrorCode.ROUTE_NOT_FOUND,
                    "No route for " + method + " " + event.getPath());
        };
    }

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) {
        Order order = orders.createOrder(readBody(event));
        return HttpResponses.created(order, collectionPath(event) + "/" + order.getId());
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) {
        return HttpResponses.ok(orders.getOrder(param(event.getPathParameters(), "id")));
    }

    private APIGatewayProxyResponseEvent deleteOrder(APIGatewayProxyRequestEvent event) {
        orders.deleteOrder(param(event.getPathParameters(), "id"));
        return HttpResponses.noContent();
    }

    private APIGatewayProxyResponseEvent findOrders(APIGatewayProxyRequestEvent event) {
        return HttpResponses.ok(orders.getOrdersByCustomer(param(event.getQueryStringParameters(), "customerId")));
    }

    private static CreateOrderRequest readBody(APIGatewayProxyRequestEvent event) {
        String body = event.getBody();
        if (body == null || body.isBlank()) {
            throw new InvalidOrderRequestException("request body is required");
        }
        if (Boolean.TRUE.equals(event.getIsBase64Encoded())) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        try {
            return HttpResponses.JSON.readValue(body, CreateOrderRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidOrderRequestException(
                    "request body must be a JSON object with customerId (string), product (string) and quantity (integer)");
        }
    }

    /**
     * Path of the collection as the client called it, e.g. {@code /prod/orders} on the default
     * execute-api endpoint, so the Location header resolves correctly including the stage.
     */
    private static String collectionPath(APIGatewayProxyRequestEvent event) {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = event.getRequestContext();
        if (requestContext != null && requestContext.getPath() != null) {
            return requestContext.getPath();
        }
        return ORDERS;
    }

    private static String param(Map<String, String> params, String name) {
        return params == null ? null : params.get(name);
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(message + "\n");
        } else {
            System.out.println(message);
        }
    }
}
