package org.example.payments.order;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(String orderId);

    // statuses 중 하나이면서 상태가 threshold 이전에 바뀐(= 그 상태로 오래 머문) 주문 — 대사 배치 대상 조회용.
    // 예: statuses = [APPROVAL_REQUESTED, CANCEL_REQUESTED], threshold = 지금 - 5분
    List<Order> findByStatusInAndStatusChangedAtBefore(Collection<OrderStatus> statuses, LocalDateTime threshold);
}
