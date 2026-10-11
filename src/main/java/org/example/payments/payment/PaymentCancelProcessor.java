package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import org.example.payments.ledger.LedgerService;
import org.example.payments.order.Order;
import org.example.payments.order.OrderItem;
import org.example.payments.order.OrderNotFoundException;
import org.example.payments.order.OrderRepository;
import org.example.payments.product.ProductRepository;
import org.example.payments.settlement.SettlementService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 결제 취소의 DB 작업을 단계별 트랜잭션으로 나눠 둔 곳. 토스 환불 호출은 이 단계들 사이(트랜잭션 밖)에서 PaymentService가 한다.
// 같은 클래스 안에서 호출하면 @Transactional이 적용되지 않으므로 PaymentService와 분리했다.
@Component
@RequiredArgsConstructor
public class PaymentCancelProcessor {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final SettlementService settlementService;
    private final LedgerService ledgerService;

    // startCancel()의 반환값. 취소 대상 주문, 토스 환불에 쓸 paymentKey, "토스를 호출해야 하는지"를 돌려준다.
    // 방금 취소를 시작한 주문과 이미 다른 요청이 취소 중인 주문(중복 요청)은 둘 다 CANCEL_REQUESTED라
    // 주문 상태만으로는 구분할 수 없기 때문이다. tossCallNeeded가 false면 토스를 다시 호출하면 안 된다(paymentKey도 null).
    public record CancelTarget(Order order, String paymentKey, boolean tossCallNeeded) {
    }

    // 트랜잭션1: PAID → CANCEL_REQUESTED + 취소 사유 저장. 재고·원장은 토스 환불이 성공한 뒤 트랜잭션2에서 반영한다
    @Transactional
    public CancelTarget startCancel(String orderId, String cancelReason) {
        Order order = findOrder(orderId);

        // 중복 요청(취소 버튼 두 번 클릭 등)이면 에러 대신 현재 상태(취소 진행 중/완료)를 그대로 돌려준다
        if (order.isCancelStartedOrDone()) {
            return new CancelTarget(order, null, false);
        }

        // PAID가 아니면(미결제, 구매확정 등) 여기서 InvalidOrderStateException
        order.markCancelRequested(cancelReason);
        return new CancelTarget(order, findPayment(order).getPaymentKey(), true);
    }

    // 트랜잭션2(성공): CANCELED로 변경 + 재고 복구 + 원장 역분개(정산 후라면 환수 기록)
    @Transactional
    public Order completeCancel(String orderId) {
        Order order = findOrder(orderId);

        // 이미 다른 경로(대사 배치 등)에서 결과가 반영됐으면 건드리지 않는다
        if (!order.isCancelInProgress()) {
            return order;
        }

        Payment payment = findPayment(order);
        order.markCanceled();
        payment.markCanceled();
        for (OrderItem item : order.getItems()) {
            productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
        }

        // 이미 정산된(PG가 지급 끝낸) 결제면 즉시 회수 불가 — 다음 정산에서 차감할 환수 대기로 남긴다
        if (settlementService.isSettled(payment)) {
            settlementService.recordClawback(payment, payment.getTotalAmount());
            ledgerService.postPaymentCanceledAfterSettlement(payment.getId(), payment.getTotalAmount());
        } else {
            ledgerService.postPaymentCanceledBeforeSettlement(payment.getId(), payment.getTotalAmount());
        }
        return order;
    }

    // 트랜잭션2(확정 실패): 토스가 환불을 확실히 거절했으므로 PAID로 되돌린다. 재고·원장은 아직 건드리지 않아 되돌릴 것이 없다
    @Transactional
    public Order failCancel(String orderId) {
        Order order = findOrder(orderId);

        if (!order.isCancelInProgress()) {
            return order;
        }

        order.markCancelRejected();
        return order;
    }

    private Order findOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Payment findPayment(Order order) {
        return paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("결제 내역이 없는 주문입니다. orderId=" + order.getOrderId()));
    }
}
