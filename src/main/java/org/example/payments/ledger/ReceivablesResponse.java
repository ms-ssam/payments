package org.example.payments.ledger;

// GET /receivables 응답 — 아직 최종 확정되지 않고 진행 중인 금액들의 스냅샷
public record ReceivablesResponse(
        long unsettledAmount,          // 미수금: 결제는 승인됐지만 PG가 아직 정산(입금) 안 한 금액 (RECEIVABLE_SETTLEMENT 잔액)
        long pendingClawbackAmount,    // 환수미수금: 정산 후 취소돼서, 아직 다음 정산에서 차감되지 않은 금액 (RECEIVABLE_CLAWBACK 잔액)
        long unearnedRevenueAmount     // 선수금: 결제는 승인됐지만 구매확정 전이라 아직 매출로 인식되지 않은 금액 (UNEARNED_REVENUE 잔액)
) {
}
