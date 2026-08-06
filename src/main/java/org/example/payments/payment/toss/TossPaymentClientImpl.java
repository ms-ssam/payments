package org.example.payments.payment.toss;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// TossPaymentClient 인터페이스의 실제 구현체 — 토스 결제 API(confirm/조회/cancel)를 진짜로 호출한다.
@Component
public class TossPaymentClientImpl implements TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClientImpl(RestClient.Builder builder, TossPaymentsProperties properties,
                                  ObjectMapper objectMapper) {
        // 앱이 시작될 때 딱 한 번, "토스랑 통신할 준비가 끝난 RestClient"를 조립해서 필드에 저장해둔다.
        // 아래 confirm/getPayment/cancel은 이미 완성된 restClient를 그대로 재사용하기만 하면 된다.
        this.restClient = builder
                // 모든 요청의 기본 주소 (예: https://api.tosspayments.com)
                .baseUrl(properties.baseUrl())
                // 모든 요청에 자동으로 붙는 인증 헤더 (Authorization: Basic base64(secretKey:))
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""))
                // 응답이 4xx/5xx 에러면 가로채서, 에러 바디({code, message})를 우리 예외(TossApiException)로 변환해 던진다.
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    TossErrorResponse error = objectMapper.readValue(response.getBody(), TossErrorResponse.class);
                    throw new TossApiException(error.code(), error.message(), response.getStatusCode());
                })
                .build();
    }

    @Override
    public TossPaymentResponse confirm(String paymentKey, String orderId, long amount) {
        return restClient.post()
                .uri("/v1/payments/confirm")
                .body(new TossConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    @Override
    public TossPaymentResponse getPayment(String paymentKey) {
        return restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    @Override
    public TossPaymentResponse cancel(String paymentKey, String cancelReason) {
        return restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(new TossCancelRequest(cancelReason))
                .retrieve()
                .body(TossPaymentResponse.class);
    }
}
