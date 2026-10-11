package org.example.payments.payment.toss;

// 토스 에러 코드 중 우리가 따로 구분해서 처리하는 것들
public enum TossErrorCode {
    ALREADY_PROCESSED_PAYMENT,      // 이미 처리된 결제 — 승인됐을 수 있음
    IDEMPOTENT_REQUEST_PROCESSING   // 같은 멱등키의 이전 요청이 아직 처리 중
}
