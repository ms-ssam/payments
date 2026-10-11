package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.payments.ledger.LedgerService;
import org.example.payments.order.Order;
import org.example.payments.order.OrderItem;
import org.example.payments.order.OrderNotFoundException;
import org.example.payments.order.OrderRepository;
import org.example.payments.order.OrderService;
import org.example.payments.payment.PaymentApprovalProcessor.ApprovalTarget;
import org.example.payments.payment.toss.TossApiException;
import org.example.payments.payment.toss.TossPaymentClient;
import org.example.payments.payment.toss.TossPaymentResponse;
import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.ProductRepository;
import org.example.payments.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentApprovalProcessor approvalProcessor;
    private final TossPaymentClient tossPaymentClient;
    private final LedgerService ledgerService;
    private final SettlementService settlementService;

    // 결제 승인. 트랜잭션1(검증·재고 차감) → 토스 승인 호출(트랜잭션 밖) → 트랜잭션2(결과 반영) 순서로 진행한다.
    // 토스를 기다리는 동안 DB 락·커넥션을 잡지 않기 위해 이 메서드 자체에는 @Transactional을 두지 않는다
    public Order confirmPayment(String orderId, String paymentKey, long clientAmount) {
        ApprovalTarget approvalTarget;
        try {
            approvalTarget = approvalProcessor.startApproval(orderId, clientAmount);
        } catch (PaymentAmountMismatchException e) {
            // 트랜잭션1이 롤백돼 주문은 PENDING 그대로다. 실패 기록은 롤백 후 따로 남긴다
            orderService.markFailed(orderId, "AMOUNT_MISMATCH");
            throw e;
        } catch (InsufficientStockException e) {
            orderService.markFailed(orderId, "OUT_OF_STOCK");
            throw e;
        }

        if (!approvalTarget.tossCallNeeded()) {
            return approvalTarget.order();
        }

        // 재요청은 TossPaymentClient.confirm의 @Retryable이 처리한다. 여기서 받는 예외는 재요청까지 실패한 결과다
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(paymentKey, orderId, approvalTarget.order().getTotalAmount());
        } catch (TossApiException e) {              // 토스가 에러로 응답함
            if (e.isDefiniteRejection()) {
                approvalProcessor.failApproval(orderId, e.getCode());
                throw e;
            }
            // 결과 불명: 토스가 승인했을 수도 있으므로 실패로 단정하지 않고 APPROVAL_REQUESTED로 남긴다(대사 배치가 정리)
            log.warn("토스 승인 결과 불명 — APPROVAL_REQUESTED로 남김. orderId={}", orderId, e);
            return approvalTarget.order();
        } catch (ResourceAccessException e) {       // 토스 응답을 못 받음(타임아웃·연결 실패)
            log.warn("토스 승인 결과 불명 — APPROVAL_REQUESTED로 남김. orderId={}", orderId, e);
            return approvalTarget.order();
        }

        return approvalProcessor.completeApproval(orderId, response);
    }

    // 주문에 담긴 상품들의 재고를 복구한다
    private void increaseStock(Order order) {
        for (OrderItem item : order.getItems()) {
            productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    // 구매확정: 선수금으로 잡혀있던 매출을 정식 매출로 인식한다. PAID 상태에서만 가능하며 이후엔 취소 불가
    @Transactional
    public Order confirmPurchase(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // PAID가 아니면 여기서 InvalidOrderStateException — 결제 내역 조회보다 먼저 해야 미결제 주문이 409로 응답된다
        order.markConfirmed();

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("PAID 주문에 결제 내역이 없습니다. orderId=" + orderId));

        ledgerService.postPurchaseConfirmed(payment.getId(), payment.getTotalAmount());

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

        // PAID가 아니면 여기서 InvalidOrderStateException — 토스 환불 호출 전에 상태 전이 가능 여부를 먼저 검사한다.
        // 토스 환불이 실패하면 예외로 트랜잭션이 롤백돼 이 상태 변경도 함께 취소된다
        order.markCanceled(cancelReason);

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("PAID 주문에 결제 내역이 없습니다. orderId=" + orderId));

        tossPaymentClient.cancel(payment.getPaymentKey(), cancelReason);

        payment.markCanceled();
        increaseStock(order);

        // 이미 정산된(PG가 지급 끝낸) 결제면 즉시 회수 불가 — 다음 정산에서 차감할 환수 대기로 남긴다
        if (settlementService.isSettled(payment)) {
            settlementService.recordClawback(payment, payment.getTotalAmount());
            ledgerService.postPaymentCanceledAfterSettlement(payment.getId(), payment.getTotalAmount());
        } else {
            ledgerService.postPaymentCanceledBeforeSettlement(payment.getId(), payment.getTotalAmount());
        }

        return order;
    }
}
