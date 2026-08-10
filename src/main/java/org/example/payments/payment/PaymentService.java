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
        decreaseStockOrThrow(order);

        try {
            // 검증된 order.getTotalAmount()로만 confirm을 요청한다
            TossPaymentResponse response = tossPaymentClient.confirm(paymentKey, orderId, order.getTotalAmount());
            return markPaid(order, response);
        } catch (TossApiException e) {
            if (TOSS_ALREADY_PROCESSED.equals(e.getCode())) {
                // 경합으로 이미 처리된 결제면 재조회해서 상태를 맞춘다
                TossPaymentResponse actual = tossPaymentClient.getPayment(paymentKey);
                if (actual.status() == TossPaymentStatus.DONE) {
                    // 이미 처리된 결제였으므로, 이번 요청이 방금 차감한 재고는 중복 차감이라 되돌린다
                    increaseStock(order);
                    return markPaid(order, actual);
                }
            }
            orderService.markFailed(orderId, e.getCode());
            throw e;
        }
    }

    // 재고를 원자적으로 차감한다. 하나라도 부족하면 실패 기록 후 예외를 던져 트랜잭션을 롤백시킨다
    private void decreaseStockOrThrow(Order order) {
        for (OrderItem item : order.getItems()) {
            int updated = productRepository.decreaseStock(item.getProduct().getId(), item.getQuantity());
            if (updated == 0) {
                orderService.markFailed(order.getOrderId(), "OUT_OF_STOCK");
                throw new InsufficientStockException(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    // 주문에 담긴 상품들의 재고를 복구한다
    private void increaseStock(Order order) {
        for (OrderItem item : order.getItems()) {
            productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    // 주문을 PAID로 전환하고, Payment가 아직 없을 때만 새로 저장한다(중복 승인 경합 시 이미 있을 수 있음)
    private Order markPaid(Order order, TossPaymentResponse response) {
        order.markPaid();
        if (paymentRepository.findByPaymentKey(response.paymentKey()).isEmpty()) {
            paymentRepository.save(toPayment(order, response));
        }
        return order;
    }

    // PAID 주문만 취소 가능하며, 토스 환불이 실제로 성공한 경우에만 주문/재고를 갱신한다
    @Transactional
    public Order cancelPayment(String orderId, String cancelReason) {
        if (cancelReason == null || cancelReason.isBlank()) {
            throw new IllegalArgumentException("취소 사유는 필수입니다.");
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidOrderStateException(orderId, order.getStatus());
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("PAID 주문에 결제 내역이 없습니다. orderId=" + orderId));

        // 토스 환불이 성공했을 때만 아래 로컬 상태를 바꾼다 — 실패하면 예외가 던져지고 아무것도 변경되지 않는다
        tossPaymentClient.cancel(payment.getPaymentKey(), cancelReason);

        order.markCanceled(cancelReason);
        payment.markCanceled();
        increaseStock(order);

        return order;
    }

    // 토스 응답을 Payment 엔티티로 변환한다
    private Payment toPayment(Order order, TossPaymentResponse response) {
        return new Payment(order, response.paymentKey(), response.method(), PaymentStatus.DONE, response.totalAmount());
    }
}
