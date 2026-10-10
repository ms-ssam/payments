package org.example.payments.order;

public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED, // 구매확정 — 선수금이 매출로 전환됨. 이후엔 취소 불가
    FAILED,
    CANCELED;

    // 이 상태에서 next 상태로 바뀔 수 있는지. 주문 상태 전이 규칙은 전부 여기에만 둔다.
    // switch에서 상태를 하나라도 빠뜨리면 컴파일 에러가 나므로, 상태를 추가하면 여기 규칙도 반드시 정하게 된다
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == FAILED;
            case PAID -> next == CONFIRMED || next == CANCELED;
            case CONFIRMED, FAILED, CANCELED -> false; // 종결 상태
        };
    }
}
