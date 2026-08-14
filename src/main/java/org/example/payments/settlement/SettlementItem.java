package org.example.payments.settlement;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.payments.payment.Payment;

// 정산 배치(Settlement)에 포함된 결제 건 1개. 이 엔티티가 존재한다는 것 자체가 "해당 결제가 정산 완료됐다"는 뜻
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    // 결제 1건은 정산에 한 번만 포함될 수 있다(중복 정산 방지)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", unique = true, nullable = false)
    private Payment payment;

    private Long grossAmount;

    private Long feeAmount;

    private Long netAmount;

    public SettlementItem(Settlement settlement, Payment payment, Long grossAmount, Long feeAmount,
                           Long netAmount) {
        this.settlement = settlement;
        this.payment = payment;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
    }
}
