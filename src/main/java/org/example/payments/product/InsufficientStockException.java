package org.example.payments.product;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("재고가 부족합니다. productId=" + productId + ", 요청수량=" + requested + ", 재고=" + available);
    }
}
