package org.example.payments.payment.toss;

import static org.example.payments.payment.toss.TossErrorCode.ALREADY_PROCESSED_PAYMENT;
import static org.example.payments.payment.toss.TossErrorCode.IDEMPOTENT_REQUEST_PROCESSING;

import org.springframework.http.HttpStatusCode;

public class TossApiException extends RuntimeException {

    private final String code;
    private final HttpStatusCode status;

    public TossApiException(String code, String message, HttpStatusCode status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    // 토스가 업무 에러(카드 거절 등)로 확실히 거절했는지 — 다시 보내도 결과가 같다.
    // 400번대여도 승인 여부를 알 수 없는 코드는 제외한다
    public boolean isDefiniteRejection() {
        return status.is4xxClientError()
                && !hasCode(ALREADY_PROCESSED_PAYMENT)
                && !hasCode(IDEMPOTENT_REQUEST_PROCESSING);
    }

    // 다시 보내면 결과가 달라질 수 있는지 — 토스 일시 오류(5xx)이거나 같은 멱등키의 이전 요청이 아직 처리 중
    public boolean isRetryable() {
        return status.is5xxServerError() || hasCode(IDEMPOTENT_REQUEST_PROCESSING);
    }

    // 토스가 보낸 에러 코드(code)가 주어진 enum 상수와 같은지.
    // name()은 Java가 모든 enum에 기본으로 제공하는 메서드로, 상수 이름을 문자열로 돌려준다(예: "ALREADY_PROCESSED_PAYMENT")
    private boolean hasCode(TossErrorCode errorCode) {
        return errorCode.name().equals(code);
    }
}
