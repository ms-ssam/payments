package org.example.payments.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.payments.order.Order;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true, nullable = false)
    private Order order;

    // 토스가 발급하는 결제 건 식별자. confirm/cancel/조회 API 호출 시 사용
    @Column(unique = true, nullable = false)
    private String paymentKey;

    private String method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private Long totalAmount;

    private LocalDateTime approvedAt;

    private LocalDateTime canceledAt;

    public Payment(Order order, String paymentKey, String method, PaymentStatus status, Long totalAmount) {
        this.order = order;
        this.paymentKey = paymentKey;
        this.method = method;
        this.status = status;
        this.totalAmount = totalAmount;
        this.approvedAt = LocalDateTime.now();
    }

    public void markCanceled() {
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
