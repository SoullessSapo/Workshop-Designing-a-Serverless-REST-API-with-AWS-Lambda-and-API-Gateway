package com.workshop.orders.application;

import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.InvalidOrderRequestException;
import com.workshop.orders.domain.Order;
import com.workshop.orders.domain.OrderNotFoundException;
import com.workshop.orders.persistence.InMemoryOrderRepository;
import com.workshop.orders.persistence.SampleOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        SampleOrders.loadInto(repository);
        AtomicInteger sequence = new AtomicInteger(100);
        service = new OrderService(repository, () -> "ORD-" + sequence.incrementAndGet());
    }

    @Test
    void createsAValidOrderWithAServerAssignedId() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertEquals(new Order("ORD-101", "CUST-42", "MacBook Air", 1), order);
    }

    @Test
    void createdOrderCanBeRetrieved() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-9", "Mouse", 3));

        assertEquals(created, service.getOrder(created.getId()));
    }

    @Test
    void trimsTextFields() {
        Order order = service.createOrder(new CreateOrderRequest("  CUST-42 ", " Monitor ", 1));

        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("Monitor", order.getProduct());
    }

    @Test
    void retrievesAnExistingOrder() {
        assertEquals(new Order("ORD-42", "CUST-7", "Keyboard", 2), service.getOrder("ORD-42"));
    }

    @Test
    void failsWhenTheOrderDoesNotExist() {
        OrderNotFoundException e = assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-999"));

        assertEquals("ORD-999", e.getOrderId());
        assertEquals("Order ORD-999 does not exist", e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankOrderId(String id) {
        assertThrows(InvalidOrderRequestException.class, () -> service.getOrder(id));
    }

    @Test
    void findsTheOrdersOfACustomerInCreationOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-42", "Charger", 1));

        assertEquals(
                java.util.List.of("ORD-001", "ORD-002", created.getId()),
                service.getOrdersByCustomer("CUST-42").stream().map(Order::getId).toList());
    }

    @Test
    void returnsAnEmptyListForACustomerWithoutOrders() {
        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankCustomerIdInSearch(String customerId) {
        InvalidOrderRequestException e =
                assertThrows(InvalidOrderRequestException.class, () -> service.getOrdersByCustomer(customerId));

        assertEquals("customerId is required", e.getMessage());
    }

    @Test
    void reportsEveryViolationOfAnInvalidOrder() {
        InvalidOrderRequestException e = assertThrows(InvalidOrderRequestException.class,
                () -> service.createOrder(new CreateOrderRequest(" ", null, null)));

        assertEquals(
                java.util.List.of("customerId is required", "product is required", "quantity is required"),
                e.getViolations());
        assertEquals("customerId is required; product is required; quantity is required", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsANonPositiveQuantity(int quantity) {
        InvalidOrderRequestException e = assertThrows(InvalidOrderRequestException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", quantity)));

        assertEquals("quantity must be greater than 0", e.getMessage());
    }

    @Test
    void rejectsAMissingRequest() {
        assertThrows(InvalidOrderRequestException.class, () -> service.createOrder(null));
    }

    @Test
    void anInvalidOrderIsNotStored() {
        assertThrows(InvalidOrderRequestException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-100", "Desk", 0)));

        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @Test
    void deletesAnExistingOrder() {
        service.deleteOrder("ORD-42");

        assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-42"));
    }

    @Test
    void deletingLeavesOtherOrdersUntouched() {
        service.deleteOrder("ORD-001");

        assertEquals(java.util.List.of("ORD-002"),
                service.getOrdersByCustomer("CUST-42").stream().map(Order::getId).toList());
    }

    @Test
    void deletingAMissingOrderFails() {
        OrderNotFoundException e = assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-999"));

        assertEquals("Order ORD-999 does not exist", e.getMessage());
    }

    @Test
    void deletingTwiceFailsTheSecondTime() {
        service.deleteOrder("ORD-42");

        assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-42"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankIdOnDelete(String id) {
        assertThrows(InvalidOrderRequestException.class, () -> service.deleteOrder(id));
    }

    @Test
    void generatesDistinctIdsByDefault() {
        String first = OrderService.randomOrderId();
        String second = OrderService.randomOrderId();

        assertTrue(first.matches("ORD-[0-9A-F]{8}"), first);
        assertTrue(!first.equals(second));
    }
}
