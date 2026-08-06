package org.example.payments.payment.toss;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Long totalAmount,
        String method,
        String approvedAt
) {
}
