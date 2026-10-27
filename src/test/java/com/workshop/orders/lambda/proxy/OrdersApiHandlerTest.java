package com.workshop.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import com.fasterxml.jackson.databind.JsonNode;
import com.workshop.orders.FailingOrderRepository;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.persistence.InMemoryOrderRepository;
import com.workshop.orders.persistence.SampleOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture B adapter, driven by the example proxy events in {@code events/proxy}, deserialized
 * with the same serializer the Lambda runtime uses for {@link APIGatewayProxyRequestEvent}.
 */
class OrdersApiHandlerTest {

    private static final PojoSerializer<APIGatewayProxyRequestEvent> EVENTS =
            LambdaEventSerializers.serializerFor(APIGatewayProxyRequestEvent.class, OrdersApiHandlerTest.class.getClassLoader());

    private OrdersApiHandler handler;

    @BeforeEach
    void setUp() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        SampleOrders.loadInto(repository);
        handler = new OrdersApiHandler(new OrderService(repository, () -> "ORD-43"));
    }

    // --- GET /orders/{id} ---------------------------------------------------------------

    @Test
    void getExistingOrderReturns200WithTheOrder() {
        APIGatewayProxyResponseEvent response = handle(event("get-order.json"));

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("{\"id\":\"ORD-42\",\"customerId\":\"CUST-7\",\"product\":\"Keyboard\",\"quantity\":2}",
                response.getBody());
    }

    @Test
    void getMissingOrderReturns404WithErrorDocument() {
        APIGatewayProxyRequestEvent event = event("get-order.json").withPathParameters(Map.of("id", "ORD-999"));

        APIGatewayProxyResponseEvent response = handle(event);

        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}", response.getBody());
    }

    // --- GET /orders?customerId= --------------------------------------------------------

    @Test
    void searchByCustomerReturns200WithTheirOrders() {
        JsonNode body = json(handle(event("find-orders.json")));

        assertEquals(2, body.size());
        assertEquals("ORD-001", body.get(0).get("id").asText());
        assertEquals("ORD-002", body.get(1).get("id").asText());
    }

    @Test
    void searchForCustomerWithoutOrdersReturns200WithEmptyArray() {
        APIGatewayProxyResponseEvent response = handle(
                event("find-orders.json").withQueryStringParameters(Map.of("customerId", "CUST-100")));

        assertEquals(200, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    void searchWithoutCustomerIdReturns400() {
        APIGatewayProxyResponseEvent response = handle(event("find-orders.json").withQueryStringParameters(null));

        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"customerId is required\"}", response.getBody());
    }

    // --- POST /orders -------------------------------------------------------------------

    @Test
    void createReturns201WithTheOrderAndItsLocation() {
        APIGatewayProxyResponseEvent response = handle(event("create-order.json"));

        assertEquals(201, response.getStatusCode());
        assertEquals("/prod/orders/ORD-43", response.getHeaders().get("Location"));
        assertEquals("{\"id\":\"ORD-43\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}",
                response.getBody());
    }

    @Test
    void createdOrderIsVisibleToLaterRequestsInTheSameEnvironment() {
        handle(event("create-order.json"));

        APIGatewayProxyResponseEvent response = handle(event("get-order.json").withPathParameters(Map.of("id", "ORD-43")));

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void createWithBrokenRulesReturns400ListingEveryViolation() {
        APIGatewayProxyResponseEvent response = handle(
                event("create-order.json").withBody("{\"customerId\":\"CUST-42\",\"quantity\":0}"));

        assertEquals(400, response.getStatusCode());
        assertEquals("INVALID_REQUEST", json(response).get("error").asText());
        assertEquals("product is required; quantity must be greater than 0", json(response).get("message").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{not json",
            "[1, 2]",
            "{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":1.5}",
            "{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":\"two\"}"})
    void createWithMalformedBodyReturns400(String body) {
        APIGatewayProxyResponseEvent response = handle(event("create-order.json").withBody(body));

        assertEquals(400, response.getStatusCode());
        assertEquals("INVALID_REQUEST", json(response).get("error").asText());
    }

    @Test
    void createWithoutBodyReturns400() {
        APIGatewayProxyResponseEvent response = handle(event("create-order.json").withBody(null));

        assertEquals(400, response.getStatusCode());
        assertEquals("request body is required", json(response).get("message").asText());
    }

    @Test
    void createAcceptsBase64EncodedBodies() {
        String raw = "{\"customerId\":\"CUST-1\",\"product\":\"Pen\",\"quantity\":5}";
        APIGatewayProxyRequestEvent event = event("create-order.json")
                .withBody(Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)))
                .withIsBase64Encoded(true);

        assertEquals(201, handle(event).getStatusCode());
    }

    // --- DELETE /orders/{id} ------------------------------------------------------------

    @Test
    void deleteExistingOrderReturns204WithoutBody() {
        APIGatewayProxyResponseEvent response = handle(event("delete-order.json"));

        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void deletedOrderIsGone() {
        handle(event("delete-order.json"));

        assertEquals(404, handle(event("get-order.json")).getStatusCode());
    }

    @Test
    void deleteMissingOrderReturns404WithErrorDocument() {
        APIGatewayProxyRequestEvent event = event("delete-order.json").withPathParameters(Map.of("id", "ORD-999"));

        APIGatewayProxyResponseEvent response = handle(event);

        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}", response.getBody());
    }

    @Test
    void deletingTwiceReturns404TheSecondTime() {
        assertEquals(204, handle(event("delete-order.json")).getStatusCode());

        assertEquals(404, handle(event("delete-order.json")).getStatusCode());
    }

    // --- Routing and unexpected failures ------------------------------------------------

    @Test
    void unsupportedMethodReturns405WithAllowHeader() {
        APIGatewayProxyResponseEvent response = handle(event("find-orders.json").withHttpMethod("PATCH"));

        assertEquals(405, response.getStatusCode());
        assertEquals("GET, POST", response.getHeaders().get("Allow"));
        assertEquals("METHOD_NOT_ALLOWED", json(response).get("error").asText());
    }

    @Test
    void unsupportedMethodOnASingleOrderListsDeleteAsAllowed() {
        APIGatewayProxyResponseEvent response = handle(event("get-order.json").withHttpMethod("PUT"));

        assertEquals(405, response.getStatusCode());
        assertEquals("GET, DELETE", response.getHeaders().get("Allow"));
    }

    @Test
    void unknownResourceReturns404() {
        APIGatewayProxyResponseEvent response = handle(
                event("find-orders.json").withResource("/customers").withPath("/customers"));

        assertEquals(404, response.getStatusCode());
        assertEquals("ROUTE_NOT_FOUND", json(response).get("error").asText());
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingDetails() {
        OrdersApiHandler broken = new OrdersApiHandler(new OrderService(new FailingOrderRepository()));

        APIGatewayProxyResponseEvent response = broken.handleRequest(event("get-order.json"), null);

        assertEquals(500, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", json(response).get("error").asText());
        assertFalse(response.getBody().contains(FailingOrderRepository.SECRET_DETAIL));
    }

    @Test
    void responseEventSerializesToTheShapeApiGatewayExpects() {
        PojoSerializer<APIGatewayProxyResponseEvent> serializer =
                LambdaEventSerializers.serializerFor(APIGatewayProxyResponseEvent.class, getClass().getClassLoader());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        serializer.toJson(handle(event("create-order.json")), out);
        String json = out.toString(StandardCharsets.UTF_8);

        assertTrue(json.contains("\"statusCode\":201"), json);
        assertTrue(json.contains("\"Location\":\"/prod/orders/ORD-43\""), json);
        assertTrue(json.contains("\"body\":\"{\\\"id\\\":\\\"ORD-43\\\""), json);
    }

    // --- helpers ------------------------------------------------------------------------

    private APIGatewayProxyResponseEvent handle(APIGatewayProxyRequestEvent event) {
        return handler.handleRequest(event, null);
    }

    private static APIGatewayProxyRequestEvent event(String file) {
        try {
            return EVENTS.fromJson(Files.readString(Path.of("events", "proxy", file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode json(APIGatewayProxyResponseEvent response) {
        try {
            return HttpResponses.JSON.readTree(response.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
