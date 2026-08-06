package org.example.payments.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("상품을 찾을 수 없습니다. productId=" + productId);
    }
}
