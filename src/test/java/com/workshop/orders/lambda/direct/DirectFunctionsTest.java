package com.workshop.orders.lambda.direct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.workshop.orders.FailingOrderRepository;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;
import com.workshop.orders.persistence.InMemoryOrderRepository;
import com.workshop.orders.persistence.SampleOrders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture A handlers, invoked as plain Java objects (no AWS needed).
 *
 * <p>Besides behaviour, these tests check the contract with API Gateway: the {@code errorMessage}
 * each failure produces must be selected by exactly the integration response we expect, using the
 * selection patterns read from {@code infra/template.yaml}.
 */
class DirectFunctionsTest {

    private static JsonNode paths;

    private OrderService service;

    @BeforeAll
    static void loadApiDefinition() throws IOException {
        // Strip CloudFormation short-form tags, which a plain YAML parser does not understand.
        String yaml = Files.readString(Path.of("infra", "template.yaml")).replaceAll("!(Sub|Ref|GetAtt) ", "");
        paths = new YAMLMapper().readTree(yaml).at("/Resources/MappingApi/Properties/Body/paths");
    }

    @BeforeEach
    void setUp() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        SampleOrders.loadInto(repository);
        service = new OrderService(repository, () -> "ORD-43");
    }

    @Test
    void createReturnsTheNewOrder() {
        Order order = new CreateOrderFunction(service)
                .handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 1), null);

        assertEquals(new Order("ORD-43", "CUST-42", "MacBook Air", 1), order);
    }

    @Test
    void getReturnsAnExistingOrder() {
        Order order = new GetOrderFunction(service).handleRequest(new OrderIdInput("ORD-42"), null);

        assertEquals("Keyboard", order.getProduct());
    }

    @Test
    void findReturnsTheCustomerOrders() {
        List<Order> orders = new FindOrdersByCustomerFunction(service)
                .handleRequest(new CustomerOrdersQuery("CUST-42"), null);

        assertEquals(2, orders.size());
    }

    @Test
    void deleteReturnsNothingAndRemovesTheOrder() {
        assertNull(new DeleteOrderFunction(service).handleRequest(new OrderIdInput("ORD-42"), null));

        assertThrows(GatewayMappedException.class,
                () -> new GetOrderFunction(service).handleRequest(new OrderIdInput("ORD-42"), null));
    }

    @Test
    void successfulDeleteSelectsThe204DefaultResponse() {
        assertEquals("204", integration("/orders/{id}", "delete").at("/responses/default/statusCode").asText());
    }

    @Test
    void deletingAMissingOrderIsReportedAs404() {
        GatewayMappedException e = assertThrows(GatewayMappedException.class,
                () -> new DeleteOrderFunction(service).handleRequest(new OrderIdInput("ORD-999"), null));

        assertEquals("ORDER_NOT_FOUND: Order ORD-999 does not exist", e.getMessage());
        assertEquals("404", selectedStatus("/orders/{id}", "delete", e.getMessage()));
    }

    @Test
    void missingOrderIsReportedWithAnOrderNotFoundPrefix() {
        GatewayMappedException e = assertThrows(GatewayMappedException.class,
                () -> new GetOrderFunction(service).handleRequest(new OrderIdInput("ORD-999"), null));

        assertEquals("ORDER_NOT_FOUND: Order ORD-999 does not exist", e.getMessage());
        assertEquals("404", selectedStatus("/orders/{id}", "get", e.getMessage()));
    }

    @Test
    void invalidInputIsReportedWithAnInvalidRequestPrefix() {
        GatewayMappedException e = assertThrows(GatewayMappedException.class,
                () -> new CreateOrderFunction(service).handleRequest(new CreateOrderRequest("CUST-42", "", 0), null));

        assertEquals("INVALID_REQUEST: product is required; quantity must be greater than 0", e.getMessage());
        assertEquals("400", selectedStatus("/orders", "post", e.getMessage()));
    }

    @Test
    void emptyInputIsAnInvalidRequest() {
        GatewayMappedException e = assertThrows(GatewayMappedException.class,
                () -> new FindOrdersByCustomerFunction(service).handleRequest(new CustomerOrdersQuery(""), null));

        assertEquals("400", selectedStatus("/orders", "get", e.getMessage()));
    }

    @Test
    void unexpectedFailuresAreReportedAsInternalErrorsWithoutDetails() {
        OrderService broken = new OrderService(new FailingOrderRepository());

        GatewayMappedException e = assertThrows(GatewayMappedException.class,
                () -> new DeleteOrderFunction(broken).handleRequest(new OrderIdInput("ORD-1"), null));

        assertEquals("INTERNAL_ERROR: Unexpected error", e.getMessage());
        assertFalse(e.getMessage().contains(FailingOrderRepository.SECRET_DETAIL));
        assertEquals("500", selectedStatus("/orders/{id}", "delete", e.getMessage()));
    }

    @Test
    void everyOperationMapsUnknownErrorsTo500() {
        // e.g. a Lambda timeout or a JSON parsing failure inside the runtime, which never reach our code.
        // Without a catch-all, such an error would fall back to the operation's default 2xx response.
        List<String> operations = new ArrayList<>();
        paths.fields().forEachRemaining(path -> path.getValue().fieldNames().forEachRemaining(method -> {
            operations.add(method + " " + path.getKey());
            assertEquals("500", selectedStatus(path.getKey(), method, "Task timed out after 15.02 seconds"),
                    method + " " + path.getKey());
        }));
        assertEquals(List.of("post /orders", "get /orders", "get /orders/{id}", "delete /orders/{id}"), operations);
    }

    /**
     * Applies every selection pattern of one operation's integration responses to an errorMessage
     * and returns the status code of the single match.
     */
    private static String selectedStatus(String resource, String method, String errorMessage) {
        List<String> statuses = new ArrayList<>();
        for (Map.Entry<String, JsonNode> response : iterable(integration(resource, method).get("responses"))) {
            String pattern = response.getKey();
            if (!"default".equals(pattern) && Pattern.compile(pattern).matcher(errorMessage).matches()) {
                statuses.add(response.getValue().get("statusCode").asText());
            }
        }
        assertEquals(1, statuses.size(), "exactly one integration response must match: " + errorMessage);
        return statuses.get(0);
    }

    private static JsonNode integration(String resource, String method) {
        JsonNode integration = paths.path(resource).path(method).path("x-amazon-apigateway-integration");
        assertTrue(integration.isObject(), "no integration for " + method + " " + resource);
        return integration;
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(JsonNode node) {
        return node::fields;
    }
}
