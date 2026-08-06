package org.example.payments.payment.toss;

public record TossConfirmRequest(String paymentKey, String orderId, Long amount) {
}
