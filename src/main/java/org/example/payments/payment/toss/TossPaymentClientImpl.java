package org.example.payments.payment.toss;

import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TossPaymentClientImpl implements TossPaymentClient {

    // 승인·취소용과 조회용은 응답 대기 제한(read timeout)만 다르고 나머지 설정은 같다
    private final RestClient paymentClient;
    private final RestClient inquiryClient;

    public TossPaymentClientImpl(RestClient.Builder builder, TossPaymentsProperties properties,
                                  ObjectMapper objectMapper) {
        // 연결 수립 제한은 모든 API 공통이라 HttpClient 하나를 두 RestClient가 공유한다
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        // 요청마다 재조립하지 않도록 앱이 시작할 때 딱 한번 인증/에러변환이 끝난 RestClient를 한 번만 만들어 재사용한다
        RestClient.Builder baseBuilder = builder
                // 모든 요청의 기본 주소 (예: https://api.tosspayments.com)
                .baseUrl(properties.baseUrl())
                // 모든 요청에 자동으로 붙는 인증 헤더 (Authorization: Basic base64(secretKey:))
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""))
                // 응답이 4xx/5xx 에러면 가로채서, 에러 바디({code, message})를 우리 예외(TossApiException)로 변환해 던진다.
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    TossErrorResponse error = objectMapper.readValue(response.getBody(), TossErrorResponse.class);
                    throw new TossApiException(error.code(), error.message(), response.getStatusCode());
                });

        this.paymentClient = baseBuilder.clone()
                .requestFactory(requestFactory(httpClient, properties.paymentReadTimeout()))
                .build();
        this.inquiryClient = baseBuilder.clone()
                .requestFactory(requestFactory(httpClient, properties.inquiryReadTimeout()))
                .build();
    }

    // 응답 대기 제한을 둔 요청 팩토리를 만든다 — 없으면 토스 응답이 늦을 때 스레드를 무한정 붙잡는다
    private static JdkClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    // 실패하면 1초 뒤 같은 멱등키로 1회 재요청한다. 재요청 대상은 TossRetryPredicate 참고
    @Retryable(predicate = TossRetryPredicate.class, maxRetries = 1, delay = 1000)
    @Override
    public TossPaymentResponse confirm(String paymentKey, String orderId, long amount) {
        return paymentClient.post()
                .uri("/v1/payments/confirm")
                // 같은 멱등키로 다시 요청하면 토스는 다시 처리하지 않고 첫 응답을 그대로 돌려준다(15일간 유효).
                // orderId는 서버가 만든 UUID이고, 실패한 주문은 다시 결제할 수 없어 주문당 승인 요청이 한 번뿐이라 그대로 쓴다.
                // 한 주문에서 결제를 여러 번 시도할 수 있게 바뀌면 시도마다 별도 키를 만들어 저장해야 한다
                .header("Idempotency-Key", orderId)
                .body(new TossConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    @Override
    public TossPaymentResponse getPayment(String paymentKey) {
        return inquiryClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    // 실패하면 1초 뒤 같은 멱등키로 1회 재요청한다. 재요청 대상은 TossRetryPredicate 참고
    @Retryable(predicate = TossRetryPredicate.class, maxRetries = 1, delay = 1000)
    @Override
    public TossPaymentResponse cancel(String paymentKey, String cancelReason, String idempotencyKey) {
        return paymentClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .body(new TossCancelRequest(cancelReason))
                .retrieve()
                .body(TossPaymentResponse.class);
    }
}
