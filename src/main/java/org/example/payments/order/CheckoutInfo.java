package org.example.payments.order;

public record CheckoutInfo(String customerKey, String orderId, String orderName, Long amount) {
}
