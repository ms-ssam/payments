package org.example.payments.payment;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.payments.order.Order;
import org.example.payments.order.OrderRepository;
import org.example.payments.order.OrderStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 대사 배치: 진행 중 상태로 오래 머문 주문을 주기적으로 찾아 토스 실제 상태에 맞춘다.
// 평소엔 대상이 0건이라 DB 조회 1번으로 끝나고 토스와 통신하지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    // 토스 결과를 아직 반영하지 못한 상태들
    private static final List<OrderStatus> IN_PROGRESS_STATUSES =
            List.of(OrderStatus.APPROVAL_REQUESTED, OrderStatus.CANCEL_REQUESTED);

    private final OrderRepository orderRepository;
    private final PaymentReconciler reconciler;
    private final ReconciliationProperties properties;

    // 실행이 끝난 뒤 interval(설정값)만큼 기다렸다가 다시 실행한다 — 오래 걸려도 두 번이 겹쳐 돌지 않는다
    @Scheduled(fixedDelayString = "${payments.reconciliation.interval}")
    public void reconcileStuckOrders() {
        LocalDateTime threshold = LocalDateTime.now().minus(properties.reconcileAfter());
        List<Order> stuckOrders = orderRepository.findByStatusInAndStatusChangedAtBefore(IN_PROGRESS_STATUSES, threshold);

        for (Order order : stuckOrders) {
            // 한 주문의 실패가 나머지 주문 처리를 막지 않도록 주문마다 모든 예외를 잡고 로그(원인 포함)만 남긴다.
            // 실패한 주문은 그대로 남아 다음 배치에서 다시 시도된다
            try {
                reconciler.reconcile(order);
            } catch (RuntimeException e) {
                log.error("대사 실패 — 다음 배치에서 다시 시도. orderId={}", order.getOrderId(), e);
            }
        }
    }
}
