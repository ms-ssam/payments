package org.example.payments.common;

import org.example.payments.order.InvalidOrderStateException;
import org.example.payments.order.OrderNotFoundException;
import org.example.payments.payment.PaymentAmountMismatchException;
import org.example.payments.payment.toss.TossApiException;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.ProductNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 상품/주문을 찾을 수 없는 경우 404로 응답한다
    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    // 재고 부족/금액 불일치/이미 종결된 주문 등 현재 상태와 충돌하는 경우 409로 응답한다
    @ExceptionHandler({InsufficientStockException.class, PaymentAmountMismatchException.class,
            InvalidOrderStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    // 잘못된 요청값인 경우 400으로 응답한다
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    // 토스가 결제 승인을 거절한 경우(카드 거절, 인증 오류 등)
    @ExceptionHandler(TossApiException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRejected(TossApiException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse(e.getCode() + ": " + e.getMessage()));
    }

    // 동일 주문에 대한 동시 상태 갱신 충돌(예: 성공 콜백 중복 도달) — Order.version이 감지
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("동시 요청으로 처리가 충돌했습니다. 주문 상태를 다시 확인해주세요."));
    }
}
