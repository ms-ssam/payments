package org.example.payments.order;

public enum OrderStatus {
    PENDING,
    APPROVAL_REQUESTED, // 재고 차감 후 토스 승인 요청 중 — 토스 결과를 아직 반영하지 못한 상태
    PAID,
    CONFIRMED, // 구매확정 — 선수금이 매출로 전환됨. 이후엔 취소 불가
    CANCEL_REQUESTED, // 토스 환불 요청 중 — 토스 결과를 아직 반영하지 못한 상태
    FAILED,
    CANCELED;

    // 이 상태에서 next 상태로 바뀔 수 있는지. 주문 상태 전이 규칙은 전부 여기에만 둔다.
    // switch에서 상태를 하나라도 빠뜨리면 컴파일 에러가 나므로, 상태를 추가하면 여기 규칙도 반드시 정하게 된다
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == APPROVAL_REQUESTED || next == FAILED;
            case APPROVAL_REQUESTED -> next == PAID || next == FAILED;
            case PAID -> next == CONFIRMED || next == CANCEL_REQUESTED;
            case CANCEL_REQUESTED -> next == CANCELED || next == PAID; // PAID: 토스가 환불을 확실히 거절함
            case CONFIRMED, FAILED, CANCELED -> false; // 종결 상태
        };
    }
}
