package org.example.payments.payment;

public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(String orderId, long expected, long actual) {
        super("결제 금액이 일치하지 않습니다. orderId=" + orderId + ", 서버금액=" + expected + ", 요청금액=" + actual);
    }
}
