package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.payments.order.Order;
import org.example.payments.payment.toss.TossPaymentClient;
import org.example.payments.payment.toss.TossPaymentResponse;
import org.springframework.stereotype.Component;

// 대사(reconcile): 진행 중 상태(APPROVAL_REQUESTED, CANCEL_REQUESTED)로 남은 주문을 토스의 실제 결제 상태에 맞춘다.
// 결제 상태는 토스가 기준이고, 주문 번호·금액은 우리 DB가 기준이다.
// 결과 반영은 승인·취소의 트랜잭션2 메서드를 그대로 쓰므로, 이미 반영된 주문이면 아무것도 하지 않는다(멱등)
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciler {

    private final TossPaymentClient tossPaymentClient;
    private final PaymentApprovalProcessor approvalProcessor;
    private final PaymentCancelProcessor cancelProcessor;

    public void reconcile(Order order) {
        TossPaymentResponse tossPaymentResponse = tossPaymentClient.getPaymentByOrderId(order.getOrderId());

        // 토스 금액이 우리 주문 금액과 다르면 다른 결제 정보일 수 있으므로 반영하지 않는다
        if (!order.getTotalAmount().equals(tossPaymentResponse.totalAmount())) {
            log.error("대사 중단 — 토스 결제 금액이 주문 금액과 다름. orderId={}, 주문금액={}, 토스금액={}",
                    order.getOrderId(), order.getTotalAmount(), tossPaymentResponse.totalAmount());
            return;
        }

        switch (order.getStatus()) {
            case APPROVAL_REQUESTED -> reconcileApproval(order, tossPaymentResponse);
            case CANCEL_REQUESTED -> reconcileCancel(order, tossPaymentResponse);
            default -> { } // 진행 중 상태가 아니면 정리할 것이 없다
        }
    }

    private void reconcileApproval(Order order, TossPaymentResponse tossPaymentResponse) {
        switch (tossPaymentResponse.status()) {
            case DONE -> approvalProcessor.completeApproval(order.getOrderId(), tossPaymentResponse);
            // 승인 실패·만료 — 토스에서 결제가 끝내 이뤄지지 않았다
            case ABORTED, EXPIRED ->
                    approvalProcessor.failApproval(order.getOrderId(), "TOSS_" + tossPaymentResponse.status());
            // IN_PROGRESS 등 — 아직 승인 전이다. 그대로 두면 토스가 시간이 지나 EXPIRED로 바꾸고, 다음 배치에서 정리된다
            default -> log.info("토스 승인 아직 진행 중 — 다음 배치에서 다시 확인. orderId={}, 토스상태={}",
                    order.getOrderId(), tossPaymentResponse.status());
        }
    }

    private void reconcileCancel(Order order, TossPaymentResponse tossPaymentResponse) {
        switch (tossPaymentResponse.status()) {
            case CANCELED -> cancelProcessor.completeCancel(order.getOrderId());
            // 토스에서 환불되지 않았다 — 결제 완료 상태로 되돌린다
            case DONE -> cancelProcessor.failCancel(order.getOrderId());
            // 부분 취소 등 이 프로젝트가 다루지 않는 상태 — 사람이 확인해야 한다
            default -> log.warn("예상하지 못한 토스 상태 — 그대로 둠. orderId={}, 토스상태={}",
                    order.getOrderId(), tossPaymentResponse.status());
        }
    }
}
