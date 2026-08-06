package org.example.payments.product;

public record ProductResponse(Long id, String name, Long price, Integer stockQuantity) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStockQuantity());
    }
}
