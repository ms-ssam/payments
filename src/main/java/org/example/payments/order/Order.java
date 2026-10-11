package org.example.payments.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.payments.buyer.Buyer;

@Entity
@Table(name = "orders") // MySQL 예약어인 order 회피
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 내부 PK(id)와 별개로 토스에 전달/콜백에서 되돌아오는 주문 식별자
    @Column(unique = true, nullable = false)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private Buyer buyer;

    // 결제위젯 화면에 표시되는 주문명(예: "티셔츠 외 2건")
    private String orderName;

    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime confirmedAt;

    // 낙관적 락: 동일 주문에 대한 동시 상태 갱신(중복 confirm 등) 충돌 감지용, Hibernate가 자동 관리
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(String orderId, Buyer buyer, String orderName, Long totalAmount) {
        this.orderId = orderId;
        this.buyer = buyer;
        this.orderName = orderName;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }

    // 승인을 이미 시작했거나 끝냈는지 — 중복 승인 요청(새로고침 등)을 걸러낼 때 쓴다
    public boolean isApprovalStartedOrDone() {
        return status == OrderStatus.APPROVAL_REQUESTED || status == OrderStatus.PAID;
    }

    // 승인 진행 중인지(APPROVAL_REQUESTED) — 이미 다른 경로에서 결과가 반영됐다면 false
    public boolean isApprovalInProgress() {
        return status == OrderStatus.APPROVAL_REQUESTED;
    }

    public void markApprovalRequested() {
        transitionTo(OrderStatus.APPROVAL_REQUESTED);
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID);
        this.paidAt = LocalDateTime.now();
    }

    public void markConfirmed() {
        transitionTo(OrderStatus.CONFIRMED);
        this.confirmedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        transitionTo(OrderStatus.FAILED);
        this.failReason = reason;
    }

    // 취소를 이미 시작했거나 끝냈는지 — 중복 취소 요청을 걸러낼 때 쓴다
    public boolean isCancelStartedOrDone() {
        return status == OrderStatus.CANCEL_REQUESTED || status == OrderStatus.CANCELED;
    }

    // 취소 진행 중인지(CANCEL_REQUESTED) — 이미 다른 경로에서 결과가 반영됐다면 false
    public boolean isCancelInProgress() {
        return status == OrderStatus.CANCEL_REQUESTED;
    }

    // 취소 사유는 요청 시점에 저장한다 — 결과 불명으로 남아도 나중에 취소를 마무리할 때 사유가 남아 있게 하기 위함
    public void markCancelRequested(String reason) {
        transitionTo(OrderStatus.CANCEL_REQUESTED);
        this.failReason = reason;
    }

    public void markCanceled() {
        transitionTo(OrderStatus.CANCELED);
    }

    // 토스가 환불을 확실히 거절해 결제 완료 상태로 되돌린다. 결제 시각(paidAt)은 그대로 둔다
    public void markCancelRejected() {
        transitionTo(OrderStatus.PAID);
        this.failReason = null;
    }

    // 상태는 이 메서드로만 바꾼다 — 허용되지 않은 전이(OrderStatus.canTransitionTo)면 예외를 던진다
    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderStateException(orderId, status);
        }
        this.status = next;
    }
}
