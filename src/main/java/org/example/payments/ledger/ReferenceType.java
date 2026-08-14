package org.example.payments.ledger;

// LedgerEntry가 어느 원본 거래(참조 대상)에서 비롯됐는지 표시 - referenceId와 함께 대사(추적)에 사용
public enum ReferenceType {
    PAYMENT,     // 결제 건(Payment) 때문에 생긴 분개
    SETTLEMENT   // 정산 배치(Settlement) 때문에 생긴 분개
}
