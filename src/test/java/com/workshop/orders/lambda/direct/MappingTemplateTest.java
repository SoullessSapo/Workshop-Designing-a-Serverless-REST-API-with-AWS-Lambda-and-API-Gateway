package com.workshop.orders.lambda.direct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Renders the Architecture A mapping templates from {@code infra/template.yaml} with Velocity 1.7
 * and small stand-ins for API Gateway's {@code $input}, {@code $util} and {@code $context}.
 *
 * <p>This is an approximation of API Gateway, not an emulator: it catches template syntax and
 * escaping mistakes before deploying, but the deployed API remains the source of truth.
 */
class MappingTemplateTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode paths;
    private static VelocityEngine velocity;

    @BeforeAll
    static void loadTemplates() throws IOException {
        // Strip CloudFormation short-form tags, which a plain YAML parser does not understand.
        String yaml = Files.readString(Path.of("infra", "template.yaml"))
                .replaceAll("!(Sub|Ref|GetAtt) ", "");
        paths = new YAMLMapper().readTree(yaml).at("/Resources/MappingApi/Properties/Body/paths");
        velocity = new VelocityEngine();
        velocity.init();
    }

    // --- request templates ------------------------------------------------------------------

    @Test
    void getOrderExtractsThePathParameter() throws IOException {
        Gateway gw = new Gateway().pathParam("id", "ORD-42");

        assertEquals(JSON.readTree("{\"id\":\"ORD-42\"}"), JSON.readTree(gw.renderRequest("/orders/{id}", "get")));
    }

    @Test
    void getOrderProducesValidJsonForAwkwardIds() throws IOException {
        Gateway gw = new Gateway().pathParam("id", "O'Brien \"quoted\"");

        assertEquals("O'Brien \"quoted\"", JSON.readTree(gw.renderRequest("/orders/{id}", "get")).get("id").asText());
    }

    @Test
    void deleteBuildsTheSameInputAsGet() throws IOException {
        Gateway gw = new Gateway().pathParam("id", "ORD-42");

        assertEquals(JSON.readTree("{\"id\":\"ORD-42\"}"), JSON.readTree(gw.renderRequest("/orders/{id}", "delete")));
    }

    @Test
    void deleteSuccessHasAnEmptyBody() throws IOException {
        // The Lambda returned null; a 204 response must not carry "null" as its body.
        Gateway gw = new Gateway().payload("null");

        assertEquals("", gw.renderResponse("/orders/{id}", "delete", "default").trim());
    }

    @Test
    void searchExtractsTheQueryParameter() throws IOException {
        Gateway gw = new Gateway().queryParam("customerId", "CUST-42");

        assertEquals(JSON.readTree("{\"customerId\":\"CUST-42\"}"), JSON.readTree(gw.renderRequest("/orders", "get")));
    }

    @Test
    void searchWithoutTheQueryParameterSendsAnEmptyValue() throws IOException {
        assertEquals(JSON.readTree("{\"customerId\":\"\"}"), JSON.readTree(new Gateway().renderRequest("/orders", "get")));
    }

    @Test
    void createPassesTheBodyThrough() throws IOException {
        Gateway gw = new Gateway().payload("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}");

        assertEquals(gw.payload, JSON.readTree(gw.renderRequest("/orders", "post")));
    }

    // --- response templates -----------------------------------------------------------------

    @Test
    void createSuccessKeepsTheBodyAndSetsLocation() throws IOException {
        Gateway gw = new Gateway()
                .payload("{\"id\":\"ORD-43\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}");

        String body = gw.renderResponse("/orders", "post", "default");

        assertEquals(gw.payload, JSON.readTree(body));
        assertEquals("/prod/orders/ORD-43", gw.responseHeaders.get("Location"));
    }

    @Test
    void notFoundErrorBecomesTheErrorDocument() throws IOException {
        Gateway gw = new Gateway().lambdaError("ORDER_NOT_FOUND: Order ORD-999 does not exist");

        String body = gw.renderResponse("/orders/{id}", "get", "^ORDER_NOT_FOUND: [\\s\\S]*");

        assertEquals(JSON.readTree("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}"),
                JSON.readTree(body));
    }

    @Test
    void notFoundMessageWithQuotesStaysValidJson() throws IOException {
        Gateway gw = new Gateway().lambdaError("ORDER_NOT_FOUND: Order O'Brien \"x\" does not exist");

        String body = gw.renderResponse("/orders/{id}", "get", "^ORDER_NOT_FOUND: [\\s\\S]*");

        assertEquals("Order O'Brien \"x\" does not exist", JSON.readTree(body).get("message").asText());
    }

    @Test
    void validationErrorBecomesTheErrorDocument() throws IOException {
        Gateway gw = new Gateway().lambdaError("INVALID_REQUEST: product is required; quantity must be greater than 0");

        String body = gw.renderResponse("/orders", "post", "^INVALID_REQUEST: [\\s\\S]*");

        assertEquals(JSON.readTree(
                        "{\"error\":\"INVALID_REQUEST\",\"message\":\"product is required; quantity must be greater than 0\"}"),
                JSON.readTree(body));
    }

    @Test
    void internalErrorHidesTheLambdaMessage() throws IOException {
        Gateway gw = new Gateway().lambdaError("INTERNAL_ERROR: Unexpected error");

        String body = gw.renderResponse("/orders/{id}", "get", "^(?!INVALID_REQUEST: |ORDER_NOT_FOUND: )[\\s\\S]+");

        assertEquals(JSON.readTree("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error. Reference: req-123\"}"),
                JSON.readTree(body));
    }

    // --- API Gateway stand-ins --------------------------------------------------------------

    /** One simulated request/response through the gateway. */
    private static final class Gateway {
        private final Map<String, String> path = new HashMap<>();
        private final Map<String, String> querystring = new HashMap<>();
        private final Map<String, String> responseHeaders = new HashMap<>();
        private JsonNode payload = JSON.createObjectNode();

        Gateway pathParam(String name, String value) {
            path.put(name, value);
            return this;
        }

        Gateway queryParam(String name, String value) {
            querystring.put(name, value);
            return this;
        }

        Gateway payload(String json) throws IOException {
            payload = JSON.readTree(json);
            return this;
        }

        Gateway lambdaError(String errorMessage) {
            payload = JSON.createObjectNode().put("errorMessage", errorMessage)
                    .put("errorType", GatewayMappedException.class.getName());
            return this;
        }

        String renderRequest(String resource, String method) {
            return render(paths.get(resource).get(method)
                    .at("/x-amazon-apigateway-integration/requestTemplates").get("application/json").asText());
        }

        String renderResponse(String resource, String method, String selectionPattern) {
            return render(paths.get(resource).get(method)
                    .at("/x-amazon-apigateway-integration/responses").get(selectionPattern)
                    .at("/responseTemplates").get("application/json").asText());
        }

        private String render(String template) {
            Map<String, Object> override = new HashMap<>();
            override.put("header", responseHeaders);
            Map<String, Object> context = new HashMap<>();
            context.put("path", "/prod" + (path.isEmpty() ? "/orders" : "/orders/" + path.get("id")));
            context.put("requestId", "req-123");
            context.put("responseOverride", override);

            VelocityContext vtl = new VelocityContext();
            vtl.put("input", new Input(payload, Map.of("path", path, "querystring", querystring, "header", Map.of())));
            vtl.put("util", new Util());
            vtl.put("context", context);
            StringWriter out = new StringWriter();
            velocity.evaluate(vtl, out, "mapping-template", template);
            return out.toString();
        }
    }

    /** Subset of API Gateway's {@code $input}: json(), path() and params() for "$" and "$.field". */
    public static final class Input {
        private final JsonNode payload;
        private final Map<String, Map<String, String>> params;

        Input(JsonNode payload, Map<String, Map<String, String>> params) {
            this.payload = payload;
            this.params = params;
        }

        public String json(String jsonPath) {
            JsonNode node = select(jsonPath);
            return node == null ? "" : node.toString();
        }

        public Object path(String jsonPath) {
            JsonNode node = select(jsonPath);
            return node == null ? null : node.isTextual() ? node.asText() : node.toString();
        }

        public Map<String, Map<String, String>> params() {
            return params;
        }

        private JsonNode select(String jsonPath) {
            return "$".equals(jsonPath) ? payload : payload.get(jsonPath.substring(2));
        }
    }

    /** Subset of API Gateway's {@code $util}. API Gateway's escapeJavaScript also turns ' into \'. */
    public static final class Util {
        public String escapeJavaScript(String value) {
            return StringEscapeUtils.escapeJavaScript(value);
        }
    }
}
