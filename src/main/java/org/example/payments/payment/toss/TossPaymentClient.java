package org.example.payments.payment.toss;

public interface TossPaymentClient {

    TossPaymentResponse confirm(String paymentKey, String orderId, long amount);

    TossPaymentResponse getPayment(String paymentKey);

    TossPaymentResponse cancel(String paymentKey, String cancelReason);
}
