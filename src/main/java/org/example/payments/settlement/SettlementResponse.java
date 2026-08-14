package org.example.payments.settlement;

import java.time.LocalDate;

public record SettlementResponse(LocalDate settlementDate, Long grossAmount, Long feeAmount, Long clawbackAmount,
                                  Long netAmount, long itemCount) {

    public static SettlementResponse from(Settlement settlement, long itemCount) {
        return new SettlementResponse(settlement.getSettlementDate(), settlement.getGrossAmount(),
                settlement.getFeeAmount(), settlement.getClawbackAmount(), settlement.getNetAmount(), itemCount);
    }
}
