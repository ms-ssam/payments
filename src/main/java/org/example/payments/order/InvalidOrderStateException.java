package org.example.payments.order;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String orderId, OrderStatus status) {
        super("현재 주문 상태에서는 처리할 수 없습니다. orderId=" + orderId + ", status=" + status);
    }
}
