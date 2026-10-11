package org.example.payments.payment.toss;

public interface TossPaymentClient {

    // 결제 승인을 요청한다. orderId를 멱등키로 보내므로 같은 주문으로 다시 호출해도 중복 승인되지 않는다
    TossPaymentResponse confirm(String paymentKey, String orderId, long amount);

    // 주문 번호로 결제의 현재 상태를 조회한다. orderId는 서버가 만든 값이라 브라우저가 넘긴 paymentKey보다 믿을 수 있다
    TossPaymentResponse getPaymentByOrderId(String orderId);

    // 결제를 취소한다. 같은 idempotencyKey로 다시 호출하면 토스는 다시 처리하지 않고 첫 응답을 돌려준다
    TossPaymentResponse cancel(String paymentKey, String cancelReason, String idempotencyKey);
}
