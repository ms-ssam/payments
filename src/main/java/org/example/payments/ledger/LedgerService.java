package org.example.payments.ledger;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    // 결제 승인: 미수금(자산) 증가 / 선수금(부채) 증가 — 아직 매출로 인식하지 않는다
    @Transactional
    public void postPaymentApproved(Long paymentId, Long amount) {
        post(List.of(
                new LedgerEntry(LedgerAccount.RECEIVABLE_SETTLEMENT, EntryType.DEBIT, amount,
                        ReferenceType.PAYMENT, paymentId),
                new LedgerEntry(LedgerAccount.UNEARNED_REVENUE, EntryType.CREDIT, amount,
                        ReferenceType.PAYMENT, paymentId)
        ));
    }

    // 구매확정: 선수금(부채) 감소 / 매출(수익) 증가 — 이 시점에 비로소 매출로 인식
    @Transactional
    public void postPurchaseConfirmed(Long paymentId, Long amount) {
        post(List.of(
                new LedgerEntry(LedgerAccount.UNEARNED_REVENUE, EntryType.DEBIT, amount,
                        ReferenceType.PAYMENT, paymentId),
                new LedgerEntry(LedgerAccount.REVENUE, EntryType.CREDIT, amount,
                        ReferenceType.PAYMENT, paymentId)
        ));
    }

    // 정산 전 취소: 선수금(부채) 감소 / 미수금(자산) 감소 — 결제 승인 분개를 그대로 역분개
    @Transactional
    public void postPaymentCanceledBeforeSettlement(Long paymentId, Long amount) {
        post(List.of(
                new LedgerEntry(LedgerAccount.UNEARNED_REVENUE, EntryType.DEBIT, amount,
                        ReferenceType.PAYMENT, paymentId),
                new LedgerEntry(LedgerAccount.RECEIVABLE_SETTLEMENT, EntryType.CREDIT, amount,
                        ReferenceType.PAYMENT, paymentId)
        ));
    }

    // 정산 후 취소: 선수금(부채) 감소 / 환수미수금(자산) 증가 — 이미 정산된 돈이라 다음 정산에서 상계해야 함
    @Transactional
    public void postPaymentCanceledAfterSettlement(Long paymentId, Long amount) {
        post(List.of(
                new LedgerEntry(LedgerAccount.UNEARNED_REVENUE, EntryType.DEBIT, amount,
                        ReferenceType.PAYMENT, paymentId),
                new LedgerEntry(LedgerAccount.RECEIVABLE_CLAWBACK, EntryType.CREDIT, amount,
                        ReferenceType.PAYMENT, paymentId)
        ));
    }

    // 정산 배치 완료: 미수금(자산) gross만큼 감소, 그 형태가 현금(자산)+수수료(비용)+상계된 환수미수금(자산 감소)으로 바뀜
    @Transactional
    public void postSettlementCompleted(Long settlementId, Long grossAmount, Long feeAmount,
                                         Long clawbackAppliedAmount, Long netAmount) {
        List<LedgerEntry> lines = new ArrayList<>();
        if (netAmount > 0) {
            lines.add(new LedgerEntry(LedgerAccount.CASH_BANK, EntryType.DEBIT, netAmount,
                    ReferenceType.SETTLEMENT, settlementId));
        }
        if (feeAmount > 0) {
            lines.add(new LedgerEntry(LedgerAccount.FEE_EXPENSE, EntryType.DEBIT, feeAmount,
                    ReferenceType.SETTLEMENT, settlementId));
        }
        if (clawbackAppliedAmount > 0) {
            lines.add(new LedgerEntry(LedgerAccount.RECEIVABLE_CLAWBACK, EntryType.DEBIT, clawbackAppliedAmount,
                    ReferenceType.SETTLEMENT, settlementId));
        }
        lines.add(new LedgerEntry(LedgerAccount.RECEIVABLE_SETTLEMENT, EntryType.CREDIT, grossAmount,
                ReferenceType.SETTLEMENT, settlementId));
        post(lines);
    }

    // 특정 계정의 현재 잔액을 조회한다. 자산/비용 계정은 차변-대변, 부채/수익 계정은 대변-차변으로 계산한다
    @Transactional(readOnly = true)
    public long getBalance(LedgerAccount account) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccount(account);
        long debitTotal = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        long creditTotal = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        return account.getNormalBalanceSide() == EntryType.DEBIT
                ? debitTotal - creditTotal
                : creditTotal - debitTotal;
    }

    // 차변 합계와 대변 합계가 같은지 검증한 뒤 저장한다 — 모든 분개는 이 메서드를 거친다
    private void post(List<LedgerEntry> lines) {
        long debitTotal = lines.stream()
                .filter(l -> l.getEntryType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        long creditTotal = lines.stream()
                .filter(l -> l.getEntryType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        if (debitTotal != creditTotal) {
            throw new UnbalancedJournalException(debitTotal, creditTotal);
        }
        ledgerEntryRepository.saveAll(lines);
    }
}
