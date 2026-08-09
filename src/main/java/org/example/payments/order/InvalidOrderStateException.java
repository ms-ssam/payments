package org.example.payments.order;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String orderId, OrderStatus status) {
        super("이미 종결된 주문이라 처리할 수 없습니다. orderId=" + orderId + ", status=" + status);
    }
}
