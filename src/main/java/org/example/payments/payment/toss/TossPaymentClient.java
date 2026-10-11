package org.example.payments.payment.toss;

public interface TossPaymentClient {

    // 결제 승인을 요청한다. orderId를 멱등키로 보내므로 같은 주문으로 다시 호출해도 중복 승인되지 않는다
    TossPaymentResponse confirm(String paymentKey, String orderId, long amount);

    // 결제 건의 현재 상태를 조회한다
    TossPaymentResponse getPayment(String paymentKey);

    // 결제를 취소한다
    TossPaymentResponse cancel(String paymentKey, String cancelReason);
}
