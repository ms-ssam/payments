package org.example.payments.payment.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(String clientKey, String secretKey, String baseUrl) {
}
