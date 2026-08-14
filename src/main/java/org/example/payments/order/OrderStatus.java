package org.example.payments.order;

public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED, // 구매확정 — 선수금이 매출로 전환됨. 이후엔 취소 불가
    FAILED,
    CANCELED
}
