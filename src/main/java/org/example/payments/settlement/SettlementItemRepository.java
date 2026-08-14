package org.example.payments.settlement;

import org.example.payments.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    boolean existsByPayment(Payment payment);

    long countBySettlement(Settlement settlement);
}
