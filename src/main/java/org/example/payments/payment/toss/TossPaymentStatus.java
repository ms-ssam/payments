package org.example.payments.payment.toss;

// 토스 결제의 상태값 — 토스 문서에 명시된 고정 목록
public enum TossPaymentStatus {
    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED
}
