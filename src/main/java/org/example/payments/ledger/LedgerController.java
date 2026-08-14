package org.example.payments.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/receivables")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    // 미수금/환수미수금/선수금 3개 계정의 현재 잔액을 조회한다 — "지금 아직 안 끝난 돈이 얼마인지"를 보여주는 API
    @GetMapping
    public ReceivablesResponse get() {
        return new ReceivablesResponse(
                ledgerService.getBalance(LedgerAccount.RECEIVABLE_SETTLEMENT),
                ledgerService.getBalance(LedgerAccount.RECEIVABLE_CLAWBACK),
                ledgerService.getBalance(LedgerAccount.UNEARNED_REVENUE));
    }
}
