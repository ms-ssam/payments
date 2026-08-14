package org.example.payments.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.payments.payment.Payment;

// 이미 정산된 결제가 취소됐을 때 생기는 "돌려받아야 할 돈" 기록. 다음 정산 배치에서 지급액을 깎는 방식으로 처리된다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundClawback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", unique = true, nullable = false)
    private Payment payment;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClawbackStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime clearedAt;

    // 어느 정산 배치에서 차감 처리됐는지(처리 전에는 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleared_in_settlement_id")
    private Settlement clearedInSettlement;

    public RefundClawback(Payment payment, Long amount) {
        this.payment = payment;
        this.amount = amount;
        this.status = ClawbackStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markCleared(Settlement settlement) {
        this.status = ClawbackStatus.CLEARED;
        this.clearedInSettlement = settlement;
        this.clearedAt = LocalDateTime.now();
    }
}
