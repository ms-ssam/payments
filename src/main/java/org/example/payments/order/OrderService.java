package org.example.payments.order;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.payments.buyer.Buyer;
import org.example.payments.buyer.BuyerRepository;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.Product;
import org.example.payments.product.ProductNotFoundException;
import org.example.payments.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BuyerRepository buyerRepository;

    // 상품 재고를 확인만 하고 차감 없이 PENDING 주문을 생성한다
    @Transactional
    public Order createOrder(Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 재고는 확인만 하고, 실제 차감은 결제 승인 콜백에서 수행한다
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStockQuantity());
        }

        Buyer buyer = buyerRepository.findAll().getFirst();

        OrderItem item = new OrderItem(product, quantity);
        Order order = new Order(UUID.randomUUID().toString(), buyer, product.getName(), item.getSubtotal());
        order.addItem(item);

        return orderRepository.save(order);
    }

    // orderId로 주문을 조회한다
    @Transactional(readOnly = true)
    public Order getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    // 체크아웃 화면에 필요한 값만 트랜잭션 안에서 꺼내 DTO로 반환한다(엔티티를 트랜잭션 밖으로 내보내지 않기 위함)
    @Transactional(readOnly = true)
    public CheckoutInfo getCheckoutInfo(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return new CheckoutInfo(order.getBuyer().getCustomerKey(), order.getOrderId(), order.getOrderName(),
                order.getTotalAmount());
    }

    // 주문을 실패 처리한다. 호출자의 트랜잭션이 나중에 롤백돼도 이 기록은 남도록 별도 트랜잭션(REQUIRES_NEW)으로 즉시 커밋한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String orderId, String reason) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.PENDING) {
            order.markFailed(reason);
        }
    }
}
