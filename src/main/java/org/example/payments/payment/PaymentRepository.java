package org.example.payments.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.payments.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder(Order order);

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findByStatusAndApprovedAtBetween(PaymentStatus status, LocalDateTime start, LocalDateTime end);
}
