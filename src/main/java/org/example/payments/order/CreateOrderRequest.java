package org.example.payments.order;

public record CreateOrderRequest(Long productId, Integer quantity) {
}
