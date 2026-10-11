package org.example.payments.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 대사 배치 설정. 실행 주기(interval)는 @Scheduled가 설정값을 직접 읽는다
@ConfigurationProperties(prefix = "payments.reconciliation")
public record ReconciliationProperties(
        Duration reconcileAfter // 진행 중 상태로 이 시간이 지난 주문부터 대사한다 — 아직 처리 중인 주문을 건드리지 않기 위함
) {
}
