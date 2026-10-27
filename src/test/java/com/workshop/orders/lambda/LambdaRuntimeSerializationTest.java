package com.workshop.orders.lambda;

import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.factories.JacksonFactory;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Part II investigation: how does Lambda turn JSON into Java objects and back?
 *
 * <p>For a {@code RequestHandler<I, O>} with POJO types, the Java runtime uses
 * {@code aws-lambda-java-serialization}'s {@link JacksonFactory} (a shaded Jackson). These tests run
 * that exact serializer locally, so the conclusions in the README are checked, not guessed.
 */
class LambdaRuntimeSerializationTest {

    @Test
    void jsonPropertiesAreBoundThroughSetters() throws IOException {
        CreateOrderRequest request = serializer(CreateOrderRequest.class)
                .fromJson(Files.readString(Path.of("events", "direct", "create-order.json")));

        assertEquals("CUST-42", request.getCustomerId());
        assertEquals("MacBook Air", request.getProduct());
        assertEquals(1, request.getQuantity());
    }

    @Test
    void unknownPropertiesAreIgnored() throws IOException {
        // create-order-2.json contains "giftWrap": true, which CreateOrderRequest does not have
        CreateOrderRequest request = serializer(CreateOrderRequest.class)
                .fromJson(Files.readString(Path.of("events", "direct", "create-order-2.json")));

        assertEquals(3, request.getQuantity());
    }

    @Test
    void missingPropertiesStayNull() {
        CreateOrderRequest request = serializer(CreateOrderRequest.class).fromJson("{\"customerId\":\"CUST-1\"}");

        assertNull(request.getProduct());
        assertNull(request.getQuantity());
    }

    @Test
    void numericStringsAreCoercedButNonNumbersFail() {
        assertEquals(2, serializer(CreateOrderRequest.class).fromJson("{\"quantity\":\"2\"}").getQuantity());

        assertThrows(RuntimeException.class,
                () -> serializer(CreateOrderRequest.class).fromJson("{\"quantity\":\"two\"}"));
    }

    @Test
    void malformedJsonFailsBeforeTheHandlerRuns() {
        assertThrows(RuntimeException.class, () -> serializer(CreateOrderRequest.class).fromJson("{not json"));
    }

    @Test
    void returnValuesAreWrittenFromGetters() {
        String json = toJson(serializer(Order.class), new Order("ORD-43", "CUST-42", "MacBook Air", 1));

        assertEquals("{\"id\":\"ORD-43\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}", json);
    }

    @Test
    void immutableClassesWithoutANoArgConstructorCannotBeInput() {
        // Order has only an all-args constructor: fine as output, unusable as Lambda input.
        assertThrows(RuntimeException.class, () -> serializer(Order.class).fromJson("{\"id\":\"ORD-1\"}"));
    }

    @Test
    void recordsAreSupportedAsInputThroughTheirCanonicalConstructor() {
        // Contrary to the first assumption recorded in the README: Jackson's record support works
        // even though the runtime disables constructor auto-detection for ordinary classes.
        assertEquals("ORD-1", serializer(RecordInput.class).fromJson("{\"id\":\"ORD-1\"}").id());
    }

    @Test
    void listsSerializeAsJsonArrays() {
        @SuppressWarnings("unchecked")
        PojoSerializer<Object> listSerializer = JacksonFactory.getInstance().getSerializer((java.lang.reflect.Type) List.class);

        assertEquals("[{\"id\":\"ORD-42\",\"customerId\":\"CUST-7\",\"product\":\"Keyboard\",\"quantity\":2}]",
                toJson(listSerializer, List.of(new Order("ORD-42", "CUST-7", "Keyboard", 2))));
    }

    record RecordInput(String id) {
    }

    private static <T> PojoSerializer<T> serializer(Class<T> type) {
        return JacksonFactory.getInstance().getSerializer(type);
    }

    private static <T> String toJson(PojoSerializer<T> serializer, T value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.toJson(value, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
