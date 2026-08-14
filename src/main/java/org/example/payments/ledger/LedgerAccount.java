package org.example.payments.ledger;

public enum LedgerAccount {
    RECEIVABLE_SETTLEMENT(EntryType.DEBIT),  // 미수금(정산예정금) - 자산: 차변이 증가
    RECEIVABLE_CLAWBACK(EntryType.CREDIT),   // 환수미수금(정산 후 취소로 PG에게 되돌려줘야 할 금액) - 부채: 대변이 증가
    UNEARNED_REVENUE(EntryType.CREDIT),      // 선수금(계약부채) - 부채: 대변이 증가
    REVENUE(EntryType.CREDIT),                // 매출 - 수익: 대변이 증가
    FEE_EXPENSE(EntryType.DEBIT),              // 지급수수료 - 비용: 차변이 증가
    CASH_BANK(EntryType.DEBIT);                // 보통예금 - 자산: 차변이 증가

    // 이 계정의 "정상 잔액" 방향 — 잔액 계산 시 어느 쪽에서 반대쪽을 빼야 하는지 결정한다
    private final EntryType normalBalanceSide;

    LedgerAccount(EntryType normalBalanceSide) {
        this.normalBalanceSide = normalBalanceSide;
    }

    public EntryType getNormalBalanceSide() {
        return normalBalanceSide;
    }
}
