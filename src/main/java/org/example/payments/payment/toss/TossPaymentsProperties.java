package org.example.payments.payment.toss;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(String clientKey, String secretKey, String baseUrl,
                                     Duration connectTimeout,       // 연결 수립 제한 (모든 API 공통)
                                     Duration paymentReadTimeout,   // 승인·취소 응답 대기 제한 (카드사를 거쳐 느릴 수 있음)
                                     Duration inquiryReadTimeout) { // 결제 조회 응답 대기 제한
}
