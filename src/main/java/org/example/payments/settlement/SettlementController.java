package org.example.payments.settlement;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    // 특정 날짜의 정산 배치를 실행한다. 대상이 없으면 204, 재실행해도 이미 정산된 건은 대상에서 빠지므로 멱등
    @PostMapping
    public ResponseEntity<SettlementResponse> run(@RequestParam LocalDate date) {
        return settlementService.runSettlement(date)
                .map(settlement -> ResponseEntity.ok(
                        SettlementResponse.from(settlement, settlementService.countItems(settlement))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    // 특정 날짜에 실행된 정산 배치들을 조회한다(같은 날짜에 여러 번 실행됐다면 배치별로 각각 반환)
    @GetMapping("/{date}")
    public List<SettlementResponse> get(@PathVariable LocalDate date) {
        return settlementService.getSettlements(date).stream()
                .map(settlement -> SettlementResponse.from(settlement, settlementService.countItems(settlement)))
                .toList();
    }
}
