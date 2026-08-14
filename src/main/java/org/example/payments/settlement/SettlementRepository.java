package org.example.payments.settlement;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    // 수동 트리거라 같은 날짜에 배치가 여러 번 실행될 수 있어(예: 하루 안에 새 결제가 더 들어와 재실행) 날짜당 1건을 보장하지 않는다
    List<Settlement> findAllBySettlementDate(LocalDate settlementDate);
}
