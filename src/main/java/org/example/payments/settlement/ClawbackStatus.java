package org.example.payments.settlement;

// 정산 후 취소로 생긴 환수 대기 기록(RefundClawback)이 다음 정산에서 차감 처리됐는지 표시
public enum ClawbackStatus {
    PENDING, // 아직 차감되지 않음 — 다음 정산 배치의 지급액에서 이 금액만큼 뺄 대상
    CLEARED  // 어느 정산 배치에서 이미 차감 처리됨
}
