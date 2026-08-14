package org.example.payments.settlement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundClawbackRepository extends JpaRepository<RefundClawback, Long> {

    List<RefundClawback> findByStatusOrderByCreatedAtAsc(ClawbackStatus status);
}
