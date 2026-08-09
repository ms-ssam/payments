package org.example.payments.payment;

import org.example.payments.order.Order;

public record PaymentResultResponse(String orderId, String status, Long amount, String failReason) {

    public static PaymentResultResponse from(Order order) {
        return new PaymentResultResponse(order.getOrderId(), order.getStatus().name(), order.getTotalAmount(),
                order.getFailReason());
    }
}
