package org.example.payments.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 특정 날짜에 실행된 정산 배치 1건. PG가 "이 날짜 결제 건들을 정산해 얼마를 지급했다"고 알려준 결과를 나타낸다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate settlementDate;

    // 정산 대상 결제 금액 합계
    private Long grossAmount;

    // PG 수수료 합계
    private Long feeAmount;

    // 이번 배치에서 차감된(정산 후 취소로 생긴) 환수 금액 합계
    private Long clawbackAmount;

    // 실제 지급액 = grossAmount - feeAmount - clawbackAmount
    private Long netAmount;

    private LocalDateTime completedAt;

    public Settlement(LocalDate settlementDate, Long grossAmount, Long feeAmount, Long clawbackAmount,
                       Long netAmount) {
        this.settlementDate = settlementDate;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.clawbackAmount = clawbackAmount;
        this.netAmount = netAmount;
        this.completedAt = LocalDateTime.now();
    }
}
