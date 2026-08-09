package org.example.payments.payment.toss;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        TossPaymentStatus status,
        Long totalAmount,
        String method,
        String approvedAt
) {
}
