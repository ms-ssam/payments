package org.example.payments.ledger;

// LedgerEntry 한 줄이 차변/대변 중 어느 쪽인지 표시
public enum EntryType {
    DEBIT,  // 차변
    CREDIT  // 대변
}
