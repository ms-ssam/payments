package org.example.payments.settlement;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 학습용 고정 수수료율. 실제 Toss 수수료와 무관하며, 실무에서는 PG가 계산해 알려주는 값이다
@ConfigurationProperties(prefix = "payments.settlement")
public record SettlementProperties(BigDecimal feeRate) {
}
