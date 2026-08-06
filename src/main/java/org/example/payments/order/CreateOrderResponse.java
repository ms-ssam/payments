package org.example.payments.order;

public record CreateOrderResponse(String orderId, String orderName, Long amount) {

    public static CreateOrderResponse from(Order order) {
        return new CreateOrderResponse(order.getOrderId(), order.getOrderName(), order.getTotalAmount());
    }
}
