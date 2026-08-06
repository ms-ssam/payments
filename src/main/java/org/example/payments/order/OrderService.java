package org.example.payments.order;

import java.util.UUID;
import org.example.payments.buyer.Buyer;
import org.example.payments.buyer.BuyerRepository;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.Product;
import org.example.payments.product.ProductNotFoundException;
import org.example.payments.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BuyerRepository buyerRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         BuyerRepository buyerRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.buyerRepository = buyerRepository;
    }

    @Transactional
    public Order createOrder(Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 생성 시점엔 재고를 "확인"만 하고 차감하지 않는다. 실제 차감은 결제 승인 콜백에서 원자적으로 수행한다.
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStockQuantity());
        }

        Buyer buyer = buyerRepository.findAll().getFirst();

        OrderItem item = new OrderItem(product, quantity);
        Order order = new Order(UUID.randomUUID().toString(), buyer, product.getName(), item.getSubtotal());
        order.addItem(item);

        return orderRepository.save(order);
    }
}
