package org.example.payments.ledger;

public class UnbalancedJournalException extends RuntimeException {

    public UnbalancedJournalException(long debitTotal, long creditTotal) {
        super("차변 합계와 대변 합계가 일치하지 않습니다. debit=" + debitTotal + ", credit=" + creditTotal);
    }
}
