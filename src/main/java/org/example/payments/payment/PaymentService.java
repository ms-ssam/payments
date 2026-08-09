package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import org.example.payments.order.InvalidOrderStateException;
import org.example.payments.order.Order;
import org.example.payments.order.OrderItem;
import org.example.payments.order.OrderNotFoundException;
import org.example.payments.order.OrderRepository;
import org.example.payments.order.OrderService;
import org.example.payments.order.OrderStatus;
import org.example.payments.payment.toss.TossApiException;
import org.example.payments.payment.toss.TossPaymentClient;
import org.example.payments.payment.toss.TossPaymentResponse;
import org.example.payments.payment.toss.TossPaymentStatus;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TOSS_ALREADY_PROCESSED = "ALREADY_PROCESSED_PAYMENT";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final TossPaymentClient tossPaymentClient;

    // 금액 검증과 재고 차감에 성공한 경우에만 토스 결제 승인을 요청하고 주문을 PAID로 전환한다
    @Transactional
    public Order confirmPayment(String orderId, String paymentKey, long clientAmount) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 이미 PAID면 재처리하지 않고 그대로 반환(멱등)
        if (order.getStatus() == OrderStatus.PAID) {
            return order;
        }

        // PENDING이 아니면(FAILED/CANCELED로 이미 종결됨) 다시 PAID로 되돌리지 않는다
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(orderId, order.getStatus());
        }

        // clientAmount는 브라우저 쿼리파라미터 값이라 신뢰하지 않는다
        if (!order.getTotalAmount().equals(clientAmount)) {
            orderService.markFailed(orderId, "AMOUNT_MISMATCH");
            throw new PaymentAmountMismatchException(orderId, order.getTotalAmount(), clientAmount);
        }

        // 조건부 차감 실패 시 예외로 트랜잭션을 롤백시켜 재고를 원복한다
        for (OrderItem item : order.getItems()) {
            int updated = productRepository.decreaseStock(item.getProduct().getId(), item.getQuantity());
            if (updated == 0) {
                orderService.markFailed(orderId, "OUT_OF_STOCK");
                throw new InsufficientStockException(item.getProduct().getId(), item.getQuantity());
            }
        }

        TossPaymentResponse response;
        try {
            // 검증된 order.getTotalAmount()로만 confirm을 요청한다
            response = tossPaymentClient.confirm(paymentKey, orderId, order.getTotalAmount());
        } catch (TossApiException e) {
            if (TOSS_ALREADY_PROCESSED.equals(e.getCode())) {
                // 경합으로 이미 처리된 결제면 재조회해서 상태를 맞춘다
                TossPaymentResponse actual = tossPaymentClient.getPayment(paymentKey);
                if (actual.status() == TossPaymentStatus.DONE) {
                    order.markPaid();
                    paymentRepository.save(toPayment(order, actual));
                    return order;
                }
            }
            orderService.markFailed(orderId, e.getCode());
            throw e;
        }

        order.markPaid();
        paymentRepository.save(toPayment(order, response));
        return order;
    }

    // 토스 응답을 Payment 엔티티로 변환한다
    private Payment toPayment(Order order, TossPaymentResponse response) {
        return new Payment(order, response.paymentKey(), response.method(), PaymentStatus.DONE, response.totalAmount());
    }
}
