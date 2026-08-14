package org.example.payments.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.payments.ledger.LedgerService;
import org.example.payments.payment.Payment;
import org.example.payments.payment.PaymentRepository;
import org.example.payments.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final RefundClawbackRepository refundClawbackRepository;
    private final SettlementProperties settlementProperties;
    private final LedgerService ledgerService;

    // 이 결제가 이미 정산 배치에 포함됐는지(=PG가 이미 지급했는지) 여부
    @Transactional(readOnly = true)
    public boolean isSettled(Payment payment) {
        return settlementItemRepository.existsByPayment(payment);
    }

    // 이미 정산된 결제가 취소됐을 때, 다음 정산에서 차감할 환수 대기 기록을 남긴다
    @Transactional
    public void recordClawback(Payment payment, Long amount) {
        if (!isSettled(payment)) {
            throw new IllegalStateException("정산되지 않은 결제는 환수 대상이 아닙니다. paymentId=" + payment.getId());
        }
        refundClawbackRepository.save(new RefundClawback(payment, amount));
    }

    // 특정 날짜에 실행된 정산 배치들을 조회한다(수동 트리거라 같은 날짜에 여러 번 실행됐을 수 있음)
    @Transactional(readOnly = true)
    public List<Settlement> getSettlements(LocalDate settlementDate) {
        List<Settlement> settlements = settlementRepository.findAllBySettlementDate(settlementDate);
        if (settlements.isEmpty()) {
            throw new SettlementNotFoundException(settlementDate);
        }
        return settlements;
    }

    // 정산 배치에 포함된 결제 건수를 조회한다
    @Transactional(readOnly = true)
    public long countItems(Settlement settlement) {
        return settlementItemRepository.countBySettlement(settlement);
    }

    // targetDate에 승인된, 아직 정산 안 된 결제들을 모아 정산 배치를 실행한다
    @Transactional
    public Optional<Settlement> runSettlement(LocalDate targetDate) {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<Payment> candidates = paymentRepository.findByStatusAndApprovedAtBetween(PaymentStatus.DONE, start, end);
        List<Payment> unsettled = candidates.stream()
                .filter(payment -> !isSettled(payment))
                .toList();

        if (unsettled.isEmpty()) {
            return Optional.empty();
        }

        // 건별 fee를 미리 계산해 캐싱한다(두 번 계산하지 않기 위함) + gross/fee 합계 누적
        List<Long> fees = new ArrayList<>();
        long grossAmount = 0;
        long feeAmount = 0;
        for (Payment payment : unsettled) {
            long fee = calculateFee(payment.getTotalAmount());
            fees.add(fee);
            grossAmount += payment.getTotalAmount();
            feeAmount += fee;
        }
        long available = grossAmount - feeAmount;

        // 대기 중인 환수를 생성일 오래된 순으로, 지급 가능액(available) 한도까지만 차감 대상으로 선정한다(부분 차감 없음)
        List<RefundClawback> pendingClawbacks = refundClawbackRepository.findByStatusOrderByCreatedAtAsc(
                ClawbackStatus.PENDING);
        List<RefundClawback> clawbacksToClear = new ArrayList<>();
        long clawbackApplied = 0;
        for (RefundClawback clawback : pendingClawbacks) {
            if (clawbackApplied + clawback.getAmount() > available) {
                break; // 한도를 넘으면 여기서 멈추고, 이 건부터는 다음 배치로 이월한다
            }
            clawbacksToClear.add(clawback);
            clawbackApplied += clawback.getAmount();
        }
        long netAmount = available - clawbackApplied;

        // Settlement를 최종값으로 한 번에 생성/저장(id 확보)
        Settlement settlement = settlementRepository.save(
                new Settlement(targetDate, grossAmount, feeAmount, clawbackApplied, netAmount));

        // 캐싱해둔 fee를 재사용해 SettlementItem을 생성한다 — settlement를 생성자에서 필수로 받으므로 null일 수 없다
        List<SettlementItem> items = new ArrayList<>();
        for (int i = 0; i < unsettled.size(); i++) {
            Payment payment = unsettled.get(i);
            long gross = payment.getTotalAmount();
            long fee = fees.get(i);
            items.add(new SettlementItem(settlement, payment, gross, fee, gross - fee));
        }
        settlementItemRepository.saveAll(items);

        for (RefundClawback clawback : clawbacksToClear) {
            clawback.markCleared(settlement);
        }
        refundClawbackRepository.saveAll(clawbacksToClear);

        ledgerService.postSettlementCompleted(settlement.getId(), grossAmount, feeAmount, clawbackApplied, netAmount);

        return Optional.of(settlement);
    }

    // 건별 수수료를 원단위 절사(내림)로 계산한다
    private long calculateFee(long amount) {
        return BigDecimal.valueOf(amount)
                .multiply(settlementProperties.feeRate())
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }
}
