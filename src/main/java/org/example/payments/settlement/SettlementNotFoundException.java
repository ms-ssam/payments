package org.example.payments.settlement;

import java.time.LocalDate;

public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(LocalDate settlementDate) {
        super("해당 날짜의 정산 결과가 없습니다. settlementDate=" + settlementDate);
    }
}
