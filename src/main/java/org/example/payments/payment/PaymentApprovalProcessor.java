package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import org.example.payments.ledger.LedgerService;
import org.example.payments.order.Order;
import org.example.payments.order.OrderItem;
import org.example.payments.order.OrderNotFoundException;
import org.example.payments.order.OrderRepository;
import org.example.payments.payment.toss.TossPaymentResponse;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 결제 승인의 DB 작업을 단계별 트랜잭션으로 나눠 둔 곳. 토스 호출은 이 단계들 사이(트랜잭션 밖)에서 PaymentService가 한다.
// 같은 클래스 안에서 호출하면 @Transactional이 적용되지 않으므로 PaymentService와 분리했다.
@Component
@RequiredArgsConstructor
public class PaymentApprovalProcessor {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;

    // startApproval()의 반환값. 승인 대상 주문과 함께 "토스를 호출해야 하는지"를 돌려준다.
    // 방금 승인을 시작한 주문과 이미 다른 요청이 승인 중인 주문(중복 콜백)은 둘 다 APPROVAL_REQUESTED라
    // 주문 상태만으로는 구분할 수 없기 때문이다. tossCallNeeded가 false면 토스를 다시 호출하면 안 된다.
    public record ApprovalTarget(Order order, boolean tossCallNeeded) {
    }

    // 트랜잭션1: 상태 변경(APPROVAL_REQUESTED) + 금액 검증 + 재고 차감.
    // 커밋되면 재고 row 락이 풀린 상태로 토스를 호출하게 된다. 중간에 예외가 나면 이 트랜잭션 전체가 롤백된다
    @Transactional
    public ApprovalTarget startApproval(String orderId, long clientAmount) {
        Order order = findOrder(orderId);

        // 중복 요청이면 에러 대신 현재 상태를 그대로 돌려준다
        if (order.isApprovalStartedOrDone()) {
            return new ApprovalTarget(order, false);
        }

        // PENDING이 아니면 여기서 InvalidOrderStateException — 금액·재고 검사보다 먼저 상태부터 확인한다
        order.markApprovalRequested();

        // clientAmount는 브라우저 쿼리파라미터 값이라 신뢰하지 않는다
        if (!order.getTotalAmount().equals(clientAmount)) {
            throw new PaymentAmountMismatchException(orderId, order.getTotalAmount(), clientAmount);
        }

        // 하나라도 부족하면 예외로 롤백돼, 앞서 차감한 다른 상품 재고도 원복된다
        for (OrderItem item : order.getItems()) {
            int updated = productRepository.decreaseStock(item.getProduct().getId(), item.getQuantity());
            if (updated == 0) {
                throw new InsufficientStockException(item.getProduct().getId(), item.getQuantity());
            }
        }

        return new ApprovalTarget(order, true);
    }

    // 트랜잭션2(성공): PAID로 변경 + Payment 저장 + 원장 분개
    @Transactional
    public Order completeApproval(String orderId, TossPaymentResponse response) {
        Order order = findOrder(orderId);

        // 이미 다른 경로(대사 배치 등)에서 결과가 반영됐으면 건드리지 않는다
        if (!order.isAwaitingApprovalResult()) {
            return order;
        }

        order.markPaid();
        Payment payment = paymentRepository.save(new Payment(order, response.paymentKey(), response.method(),
                PaymentStatus.DONE, response.totalAmount()));
        // 아직 매출로 인식하지 않는다 — 선수금으로 잡고, 구매확정 시점에 매출로 전환한다
        ledgerService.postPaymentApproved(payment.getId(), payment.getTotalAmount());
        return order;
    }

    // 트랜잭션2(확정 실패): FAILED로 변경 + 트랜잭션1에서 차감한 재고 복구.
    // 트랜잭션1이 이미 커밋돼 롤백으로는 되돌릴 수 없으므로 재고를 직접 되돌린다
    @Transactional
    public Order failApproval(String orderId, String reason) {
        Order order = findOrder(orderId);

        if (!order.isAwaitingApprovalResult()) {
            return order;
        }

        order.markFailed(reason);
        for (OrderItem item : order.getItems()) {
            productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
        }
        return order;
    }

    private Order findOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
