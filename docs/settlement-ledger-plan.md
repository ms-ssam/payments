# 선수금 · 정산 · 원장(장부) · 미수금 처리 구현 계획 — 요약

## 배경
결제위젯 연동(카드결제, Toss)까지 끝난 스켈레톤에 이어, 결제 도메인의 핵심인 **선수금(수익인식) / 정산(Settlement) / 원장관리(Ledger) / 미수금(Receivables)**을 확장한다. 가상계좌·계좌이체는 이번에도 범위 밖. 판매자는 다건이 아닌 **단일 사업자(나 자신) 기준**으로 시작하고, 나중에 Seller를 얹어 마켓플레이스형으로 확장 가능하게 경계만 지킨다.

**전제 1 (수익인식)**: 결제가 승인됐다고 바로 매출로 잡으면 안 된다 — 구매자가 아직 취소/환불할 수 있는 상태이기 때문. 그래서 결제 승인 시점엔 **선수금(계약부채)**으로 잡고, **구매확정** 시점에야 비로소 **매출**로 전환한다. 이 프로젝트엔 배송 도메인이 없으므로 구매확정은 수동 REST API로 트리거한다.

**전제 2 (정산)**: 실무에서는 정산 계산 자체를 PG(Toss)가 수행하고 가맹점은 결과만 수신/조회한다. 이 프로젝트는 Toss의 실제 정산 API·웹훅에 접근할 수 없으므로, `SettlementService.runSettlement(date)`는 **"PG가 정산 결과를 알려줬다"는 이벤트를 우리가 직접 계산해 시뮬레이션**한 것이다. 실제 서비스라면 이 메서드 내부가 "Toss 정산 API 응답을 파싱해 반영"하는 코드로 바뀌는 지점.

**수익인식(선수금→매출)과 정산(현금흐름)은 서로 다른 이벤트로 전환되는 서로 다른 축**이다. PG(Toss) 입장에서 정산은 오직 "결제 승인일"만 보고 처리하며, 가맹점 내부의 "구매확정" 개념 자체를 모른다. 그래서 실무에서는 정산(보통 D+1~D+2)이 구매확정(배송+청약철회기간, 보통 그보다 김)보다 먼저 끝나는 경우가 흔하다 — 이게 바로 "정산 후 취소(환수)"가 실무에서 중요하게 다뤄지는 이유다. 하나의 결제 건은 아래 4가지 상태 조합 중 하나에 있을 수 있다.

| | 구매확정 전 (대변=선수금) | 구매확정 후 (대변=매출) |
|---|---|---|
| **정산 전** (차변=미수금) | ① 결제 직후 기본 상태 | ③ 매출은 인식했지만 PG가 아직 안 준 돈 — 교과서적 의미의 "미수금" |
| **정산 후** (차변=현금) | ② 돈은 이미 들어왔지만 아직 취소될 수 있는 상태 — 여기서 취소되면 "환수(clawback)" 필요 | ④ 완전히 종결 |

## 차변/대변 기본 규칙 (참고)
복식부기는 모든 거래를 차변(왼쪽)·대변(오른쪽)에 동시에 적고 항상 양쪽 합계가 같아야 한다. **차변/대변 = 증가/감소가 아니라, 계정 종류에 따라 어느 쪽이 증가인지 반대**다.

| 계정 종류 | 차변에 적히면 | 대변에 적히면 |
|---|---|---|
| 자산 (미수금·현금·환수미수금) | 증가 | 감소 |
| 부채 (선수금) | 감소 | 증가 |
| 수익 (매출) | 감소 | 증가 |
| 비용 (수수료) | 증가 | 감소 |

## 합의된 핵심 결정
- **선수금 우선 구현**: 결제 승인 = 즉시 매출이 아니라 **선수금(부채)**. "구매확정" 이벤트가 일어나야 매출로 전환. 취소는 구매확정 전(=`Order.status==PAID`)에만 가능 — 기존 취소 가드(`status != PAID`)가 자연스럽게 이를 보장.
- **미수금 = 미정산금**: PAID(결제 완료)됐지만 아직 정산 배치에 포함 안 된 금액(위 표의 ①+③). 별도 엔티티가 아니라 원장 잔액으로 조회.
- **원장 = 복식부기**, 모든 분개는 차변합=대변합 검증 통과해야 저장(무결성 핵심).
- **구매확정 트리거 = 수동 REST**(`POST /payments/{orderId}/confirm`).
- **정산 트리거 = 수동 REST**(`POST /settlements?date=`). 로직을 서비스 메서드로 캡슐화해 나중에 `@Scheduled`가 같은 메서드를 부르도록 교체 가능하게.
- **정산 후 취소(환불) 포함**: 이미 정산된 결제가 취소되면 즉시 회수(clawback) 불가 → "환수 대기"로 기록해두고 **다음 정산 배치에서 신규 매출분과 상계**해 차감 지급.
- **자동화 테스트는 이번 범위 제외** — 수동(curl) 검증으로만 확인. H2/JUnit 테스트 도입은 나중에 별도로 고려.

## 계정과목 & 분개 설계
계정: `RECEIVABLE_SETTLEMENT`(미수금-정산예정, 자산), `RECEIVABLE_CLAWBACK`(환수미수금, **부채** — 정산 후 취소로 PG에게 되돌려줘야 할 돈이라 "받을 돈"이 아니라 "갚을 돈"), `UNEARNED_REVENUE`(선수금/계약부채, 부채), `REVENUE`(매출, 수익), `FEE_EXPENSE`(지급수수료, 비용), `CASH_BANK`(보통예금, 자산)

각 계정은 `LedgerAccount` enum에 "정상잔액 방향"(자산·비용=차변, 부채·수익=대변)을 갖고 있고, `LedgerService.getBalance()`가 이를 참조해 부호를 맞춰 계산한다(전부 "차변-대변"으로 계산하면 부채/수익 계정 잔액 부호가 뒤집히는 버그가 실제로 있었음 — 아래 "구현 중 발견한 이슈" 참고).

| 이벤트 | 차변 | 대변 |
|---|---|---|
| 결제 승인(DONE) A원 | RECEIVABLE_SETTLEMENT A | UNEARNED_REVENUE A |
| **구매확정** A원 | UNEARNED_REVENUE A | REVENUE A |
| 취소(구매확정 전, 정산 전) A원 | UNEARNED_REVENUE A | RECEIVABLE_SETTLEMENT A |
| 취소(구매확정 전, 정산 후) A원 | UNEARNED_REVENUE A | RECEIVABLE_CLAWBACK A |
| 정산 배치 완료 (gross G, fee F, 이번에 상계된 clawback C, net=G-F-C) | CASH_BANK net, FEE_EXPENSE F, RECEIVABLE_CLAWBACK C | RECEIVABLE_SETTLEMENT G |

정산 배치에서 상계 가능한 clawback은 `min(대기중 clawback 합계, G-F)`까지만 — 남은 만큼은 PENDING으로 남아 다음 배치에서 이어서 상계(오래된 것부터, 부분 상계는 하지 않고 항목 단위로 처리).

## 주문 도메인 변경 (구매확정)
- `OrderStatus`에 `CONFIRMED` 추가 (PENDING → PAID → **CONFIRMED** 또는 CANCELED)
- `Order`: `confirmedAt` 필드, `markConfirmed()` 메서드 추가
- 취소(`cancelPayment`)의 기존 가드(`status != PAID`면 `InvalidOrderStateException`)가 CONFIRMED 이후 취소를 자동으로 막아줌 — 별도 수정 불필요

## 패키지 구조
```
ledger/      LedgerAccount(enum), EntryType(enum: DEBIT/CREDIT), LedgerEntry(entity),
             LedgerEntryRepository, LedgerService, UnbalancedJournalException
settlement/  Settlement, SettlementItem, RefundClawback, ClawbackStatus(enum: PENDING/CLEARED),
             SettlementRepository, SettlementItemRepository, RefundClawbackRepository,
             SettlementService, SettlementController, SettlementNotFoundException,
             SettlementProperties(@ConfigurationProperties)
```

### LedgerService (핵심)
- `post(List<LedgerEntry> lines)` — private, `sum(DEBIT) == sum(CREDIT)` 아니면 `UnbalancedJournalException`, 통과 시 저장. 모든 분개는 이 메서드를 거친다.
- `postPaymentApproved(paymentId, amount)` — 결제승인: RECEIVABLE_SETTLEMENT/UNEARNED_REVENUE
- `postPurchaseConfirmed(paymentId, amount)` — 구매확정: UNEARNED_REVENUE→REVENUE
- `postPaymentCanceledBeforeSettlement(paymentId, amount)` / `postPaymentCanceledAfterSettlement(paymentId, amount)` — 취소 역분개(UNEARNED_REVENUE 기준)
- `postSettlementCompleted(settlementId, gross, fee, clawbackApplied, net)`
- `getBalance(LedgerAccount)` — 해당 계정의 DEBIT합-CREDIT합 (미수금/선수금 조회 API가 사용)
- 메서드 파라미터는 엔티티가 아닌 id/금액 등 값 타입만 받는다 — `ledger`가 `payment`/`settlement`를 import하지 않게 해 패키지 간 순환 의존을 피함(`payment/settlement → ledger`는 있어도 `ledger → payment/settlement`는 없음)

### SettlementService
- `isSettled(Payment)`: `settlementItemRepository.existsByPayment(payment)`
- `recordClawback(Payment, amount)`: 호출 전 `isSettled(payment)`를 스스로 다시 검증(호출자를 믿지 않음) 후 `RefundClawback(PENDING)` 생성 — PaymentService가 정산후취소 시 호출
- `getSettlements(LocalDate)`: 해당 날짜에 실행된 정산 배치를 **전부** 반환(리스트) — 수동 트리거라 같은 날짜에 여러 번 실행될 수 있어 "날짜당 1건"을 보장하지 않음. 없으면 `SettlementNotFoundException`
- `runSettlement(LocalDate targetDate)` (`@Transactional`):
  1. `paymentRepository.findByStatusAndApprovedAtBetween(DONE, start, end)` 중 `isSettled==false`인 것만 대상
  2. 건별 fee = `amount * feeRate` 원단위 절사(내림), gross/fee 합산
  3. PENDING `RefundClawback`을 생성일 오름차순으로 `G-F` 한도까지 상계 대상으로 선정(부분 상계 없음)
  4. `Settlement` + `SettlementItem`들 저장, 상계된 clawback → `CLEARED` + `clearedInSettlement` 세팅
  5. `ledgerService.postSettlementCompleted(...)` 호출(같은 트랜잭션)
  6. 대상이 전혀 없으면(gross=0, 상계할 clawback도 없음) Settlement 생성 없이 빈 결과 반환

## 결제 도메인 변경 (기존 파일)
- `PaymentRepository`: `findByStatusAndApprovedAtBetween(PaymentStatus, LocalDateTime, LocalDateTime)` 추가
- `PaymentService`: `LedgerService`, `SettlementService` 의존성 추가
  - `markPaid()` 내부, `Payment` 신규 저장 시점에만(멱등 가드 안에서) `ledgerService.postPaymentApproved(...)` 호출
  - **신규** `confirmPurchase(orderId)`: `order.status==PAID`인지 확인 후 `order.markConfirmed()` + `ledgerService.postPurchaseConfirmed(...)` 호출
  - `cancelPayment()`: 기존 로직(Toss cancel → 주문/결제 상태변경 → 재고복구) 뒤에 `settlementService.isSettled(payment)`로 분기
    - 정산 전: `ledgerService.postPaymentCanceledBeforeSettlement(...)`
    - 정산 후: `settlementService.recordClawback(payment, amount)` + `ledgerService.postPaymentCanceledAfterSettlement(...)`
  - 의존 방향: `payment → settlement → ledger`, `payment → ledger` (순환 없음, 기존 `PaymentService → OrderService` 서비스 간 호출 패턴과 동일)

## REST 엔드포인트 (신규)
- `POST /payments/{orderId}/confirm` → 구매확정(매출 인식). `PAID`가 아니면 `InvalidOrderStateException`
- `POST /settlements?date=yyyy-MM-dd` → `runSettlement` 실행, 결과 JSON(gross/fee/clawback/net/itemCount) 반환. 재실행해도 이미 정산된 건은 대상에서 빠지므로 멱등.
- `GET /settlements/{date}` → 해당 날짜에 실행된 정산 배치를 **리스트**로 조회(같은 날짜에 여러 번 실행됐다면 배치별로 각각 반환, 없으면 404 `SettlementNotFoundException`)
- `GET /receivables` → `{unsettledAmount, pendingClawbackAmount, unearnedRevenueAmount}` — 각각 `RECEIVABLE_SETTLEMENT`, `RECEIVABLE_CLAWBACK`, `UNEARNED_REVENUE` 잔액

## 설정값
`application.yml`에 추가 (기존 `toss.payments.*` 패턴과 동일한 record 기반 `@ConfigurationProperties`):
```yaml
payments:
  settlement:
    fee-rate: 0.029   # 학습용 고정값. 실제 Toss 수수료와 무관함을 코드 주석으로 명시
```

## 테스트
자동화 테스트(H2 추가, JUnit 테스트 작성)는 이번 범위에서 제외 — 나중에 별도로 고려. 이번엔 수동 검증(curl/DB 조회)으로만 확인한다.

## 구현 순서
1. `ledger` 패키지: 계정(선수금 포함)/엔티티/리포지토리/서비스(`post`, 잔액조회)
2. `Order` 도메인: `CONFIRMED` 상태·`markConfirmed()` 추가
3. `settlement` 패키지 엔티티/리포지토리 + `SettlementProperties` + `application.yml` 반영
4. `SettlementService.runSettlement`(clawback 상계 제외한 기본 버전) + `SettlementController`(`POST/GET /settlements`)
5. `PaymentService` 연동: 결제승인 시 `postPaymentApproved`(선수금 인식)
6. `PaymentService.confirmPurchase` + `POST /payments/{orderId}/confirm`(매출 인식)
7. `cancelPayment` 정산 전/후 분기: `UNEARNED_REVENUE` 역분개 + 정산 후엔 `RefundClawback` 생성
8. `runSettlement`에 clawback 상계 로직 추가(부분 이월 포함)
9. `GET /receivables` 추가
10. 문서화: 기존 `docs/payment-flow-sequence.md` 스타일에 맞춰 정산/원장 흐름 다이어그램 + 시나리오 표 문서 추가 → `docs/settlement-ledger-flow-sequence.md`

## 구현 시나리오

| # | 분류 | 시나리오 | 트리거 | 기대 결과 |
|---|---|---|---|---|
| 1 | 결제 승인 | 정상 승인 | 결제 confirm 성공 | 원장에 `RECEIVABLE_SETTLEMENT A / UNEARNED_REVENUE A` 분개(매출 아님) |
| 2 | 구매확정 | 정상 확정 | `POST /payments/{orderId}/confirm` | 원장에 `UNEARNED_REVENUE A / REVENUE A` — 이때 비로소 매출 인식 |
| 3 | 구매확정 | 구매확정 후 취소 시도 | `POST /payments/{orderId}/cancel` | `InvalidOrderStateException`(기존 가드 재사용, 매출 인식 후엔 이 API로 취소 불가) |
| 4 | 정산 전 취소 | 확정 전 취소 | `POST /payments/{orderId}/cancel` | 원장 역분개(`UNEARNED_REVENUE A / RECEIVABLE_SETTLEMENT A`) |
| 5 | 정산 배치 | 신규 정산 실행 | `POST /settlements?date=` | 대상 결제 전체 `SettlementItem` 생성, `CASH_BANK+FEE_EXPENSE = RECEIVABLE_SETTLEMENT 감소분` |
| 6 | 정산 배치 | 같은 날짜 재실행 | `POST /settlements?date=`(중복 호출) | 이미 정산된 건은 대상에서 빠져 추가 분개 없음(멱등) |
| 7 | 정산 후 취소 | 확정 전, 이미 정산된 결제 취소 | `POST /payments/{orderId}/cancel` | `RefundClawback(PENDING)` 생성, 원장에 `UNEARNED_REVENUE A / RECEIVABLE_CLAWBACK A` |
| 8 | 정산 배치 | 대기 중 clawback과 함께 재정산 | `POST /settlements?date=`(신규 매출 있음) | `net = gross - fee - clawback`으로 지급, 해당 clawback `CLEARED` 전환 |
| 9 | 정산 배치 | clawback이 이번 배치 가용액(`G-F`)보다 큼 | 신규 매출이 적은 날 정산 실행 | 가용액만큼만 상계, 나머지 clawback은 `PENDING`으로 다음 배치 이월 |
| 10 | 조회 | 선수금/미수금 상태 확인 | `GET /receivables` | `unearnedRevenueAmount`는 확정 전 결제 합계, `unsettledAmount`는 정산 안 된 PAID 합계, `pendingClawbackAmount`는 미상계 환수액과 일치 |

구현 순서: 1 → 4 → 2/3 → 5/6 → 7 → 8/9 → 10. 자동화 테스트는 이번 범위 밖이며, 각 시나리오는 curl/DB 조회로 수동 검증한다.

## 검증
수동(curl) 시나리오: 결제 승인 2건(선수금 인식 확인) → 1건 구매확정(매출 전환 확인) → 정산 실행(gross/fee/net 확인, 확정 여부와 무관하게 둘 다 정산 대상) → `GET /receivables`로 미수금 0 확인 → 확정 전 상태인 나머지 1건(이미 정산됨) 취소 → `pendingClawbackAmount` 증가 확인 → 신규 결제 1건 승인 후 재정산 → clawback이 차감된 net으로 지급되고 `pendingClawbackAmount` 0으로 복귀하는지 확인

실제 curl/DB 검증 절차와 결과는 `docs/settlement-ledger-flow-sequence.md`에 정리했다.

## 구현 중 발견한 이슈
계획 단계에서는 안 보였다가 실제로 curl/DB로 검증하면서 드러난 문제 4가지. 전부 이번 커밋에서 수정됨.

1. **`LedgerService.getBalance()` 부호 버그**: 모든 계정을 "차변-대변"으로 고정 계산해서, 부채(`UNEARNED_REVENUE`)처럼 대변이 정상잔액인 계정은 잔액이 음수로 뒤집혀 나왔다. → `LedgerAccount`에 계정별 정상잔액 방향(`normalBalanceSide`)을 추가하고 `getBalance()`가 이를 참조하도록 수정.
2. **`RECEIVABLE_CLAWBACK` 계정 오분류**: 처음엔 "환수미수금"이라는 이름 때문에 자산으로 분류했는데, 실제로는 "PG에게 되돌려줘야 할 돈"이라 부채다. 1번과 같은 부호 버그로 이어짐 → 정상잔액을 `CREDIT`(부채)로 수정.
3. **MySQL 네이티브 ENUM 컬럼 잘림**: `OrderStatus`에 `CONFIRMED`를 추가했는데, 기존 `orders.status` 컬럼이 Hibernate에 의해 `ENUM('PENDING','PAID','FAILED','CANCELED')`로 이미 생성돼 있었고, `ddl-auto=update`는 기존 네이티브 ENUM 컬럼의 값 목록을 자동으로 안 넓혀준다. `CONFIRMED` 저장 시 "Data truncated" 오류 발생 → `ALTER TABLE orders MODIFY COLUMN status ENUM(...)`로 수동 반영. (신규 테이블의 ENUM 컬럼은 생성 시점에 전체 값을 포함해서 문제없음 — 기존 테이블에 새 enum 값을 추가할 때만 해당)
4. **정산 배치의 "날짜당 1건" 가정 오류**: `Settlement`가 암묵적으로 날짜당 1건이라고 가정했는데(`findBySettlementDate` → `Optional`), 정산이 수동 트리거라 같은 날짜에 여러 번 실행될 수 있다는 걸 실제 재현하고서야 발견. 두 번째 실행 시 `NonUniqueResultException` 발생 → `Settlement` 조회를 리스트 기반으로 변경, `GET /settlements/{date}`도 배치별로 각각 반환하도록 수정(합산하지 않음 — 각 배치가 독립된 기록이라 억지로 합칠 이유가 없음).
