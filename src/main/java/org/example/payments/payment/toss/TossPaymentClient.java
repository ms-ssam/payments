package org.example.payments.payment.toss;

public interface TossPaymentClient {

    // 결제 승인을 요청한다
    TossPaymentResponse confirm(String paymentKey, String orderId, long amount);

    // 결제 건의 현재 상태를 조회한다
    TossPaymentResponse getPayment(String paymentKey);

    // 결제를 취소한다
    TossPaymentResponse cancel(String paymentKey, String cancelReason);
}
