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

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markConfirmed() {
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = OrderStatus.FAILED;
        this.failReason = reason;
    }

    public void markCanceled(String reason) {
        this.status = OrderStatus.CANCELED;
        this.failReason = reason;
    }
}
