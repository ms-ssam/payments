package org.example.payments.payment.toss;

import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.web.client.ResourceAccessException;

// @Retryable이 예외가 날 때마다 "재시도할까?"를 물어보는 판단 기준. true면 재시도, false면 예외를 그대로 던진다.
// 멱등키를 함께 보내므로 재요청해도 토스에서 중복 처리되지 않는다
public class TossRetryPredicate implements MethodRetryPredicate {

    // method: 예외가 난 메서드(여기서는 쓰지 않음), throwable: 방금 발생한 예외
    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        // 토스 응답을 못 받음(타임아웃·연결 실패) → 재시도
        if (throwable instanceof ResourceAccessException) {
            return true;
        }
        // 토스가 에러로 응답함 → 다시 보내면 결과가 달라질 수 있는 에러일 때만 재시도
        if (throwable instanceof TossApiException) {
            TossApiException tossError = (TossApiException) throwable;
            return tossError.isRetryable();
        }
        return false;
    }
}
