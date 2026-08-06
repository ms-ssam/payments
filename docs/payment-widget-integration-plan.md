# 토스페이먼츠 결제위젯(v2, 주문서형) 연동 계획 — 요약

## 배경
`payments`는 Spring Boot 4.1.0 / Java 21의 빈 스켈레톤. 결제 정상/실패/취소/이탈 흐름 전반과, 그 과정에서 **결제 상태·재고·금액의 정합성**을 최소 범위로 구현하는 것이 목표.

**합의된 스코프**: 회원=고정 임시 Buyer 1건 / DB=로컬 MySQL / 웹훅=이번 범위 제외(나중에 확장 가능하게만 설계) / 인증키=토스 공개 테스트키(`gck/gsk` 접두사, 적용 전 최신값 재확인 필요) / 프론트엔드=별도 SPA 없음 — **위젯 SDK를 실제로 mount해야 하는 체크아웃 화면 1개만 Thymeleaf(SSR), 나머지는 전부 REST(JSON)로 만들어 데이터로 확인**

## 결제 흐름
1. `GET /products`(REST) 상품 목록 조회 → `POST /orders`(REST, `{productId, quantity}`): **재고 차감 없이** `Order(PENDING)` 생성만 (금액은 이 시점 계산해 저장), JSON으로 `orderId/orderName/amount` 응답
2. `GET /orders/{orderId}/checkout` (Thymeleaf, **유일한 SSR 화면** — 토스 위젯 SDK가 DOM에 mount되고 성공/실패 시 브라우저가 실제로 리다이렉트되는 지점이라 HTML이 필수): 위젯 렌더 → `requestPayment(orderId, amount, successUrl, failUrl)`
3. 성공 → `GET /payments/success?paymentKey,orderId,amount`(REST, JSON 응답) → 서버 저장 금액과 대조 → **재고 조건부 차감 성공 시에만** Toss confirm 호출 → `Order=PAID`
4. 재고 부족 또는 Toss 거절 → confirm 자체를 안 부르거나 실패 응답 → `Order=FAILED` (재고는 트랜잭션 롤백으로 자동 복구, 별도 복구 코드 불필요)
5. `GET /payments/fail`(REST, JSON 응답 — 사용자가 위젯에서 결제 자체를 실패/취소한 경우) → `Order=FAILED`
6. 취소(결제 완료 후) → `POST /payments/{orderId}/cancel`(REST) → Toss cancel → `Order=CANCELED` + 재고 명시적 복구(이 경우만 복구 코드 필요 — 이미 커밋된 확정 재고이므로)
7. 결제 중 이탈(콜백 자체가 안 옴) → 재고를 아직 안 건드렸으므로 **영향 없음**. 주문은 PENDING으로 남아도 무해 → 만료 스케줄러 불필요(범위에서 제외)

## 정합성 핵심 결정
- **재고 차감 시점 = 결제 승인 콜백, confirm 호출 직전**: 주문 생성 시엔 차감하지 않음 → 이탈해도 재고가 묶이지 않고, 별도 만료/정리 스케줄러가 필요 없어짐
- **차감+confirm을 하나의 DB 트랜잭션으로 묶음**: "조건부 UPDATE로 재고 차감 → 성공 시에만 Toss confirm 호출" 순서를 지키면, 재고 부족이든 Toss 거절이든 실패 시 트랜잭션이 자동 롤백되어 차감이 **코드 없이 자동 복구**됨. 실패 상태(`Order=FAILED`, 사유) 기록만 별도 트랜잭션(`REQUIRES_NEW`)으로 커밋해 롤백에 휩쓸리지 않게 함
- **재고 동시성 = DB 조건부 UPDATE만으로 충분**: `UPDATE product SET stock = stock - :qty WHERE stock >= :qty`. MySQL InnoDB가 해당 row에 자동으로 배타적 락을 걸어 동시 요청을 직렬화하므로 Redis 분산락 등 애플리케이션 레벨 락은 불필요(인프라 의존성만 늘어남). 대규모 트래픽으로 DB row lock 자체가 병목이 되면 그때 Redis 락으로 전환 검토
- **금액 위변조 방지**: 콜백 쿼리의 amount를 신뢰하지 않고 서버가 주문 생성 시 저장한 금액과 대조, 불일치 시 재고 차감/confirm 모두 시도하지 않고 즉시 FAILED
- **중복 승인 방지**: 이미 PAID면 confirm 재호출 안 함(멱등) + Toss가 `ALREADY_PROCESSED_PAYMENT`로 2차 방어 + `Payment` unique 제약이 최후 안전망
- **웹훅 확장 대비**: 성공/실패/취소가 모두 거치는 단일 상태갱신 지점으로 모듈화 → 나중에 웹훅 컨트롤러가 이 지점만 재사용하면 됨

## 구조
```
buyer/   Buyer, BuyerRepository
product/ Product, ProductRepository, ProductController(@RestController)
order/   Order, OrderItem, OrderStatus, OrderService,
         OrderController(@Controller — POST /orders는 @ResponseBody로 JSON, GET checkout만 뷰 반환)
payment/ Payment, PaymentStatus, PaymentService, PaymentController(@RestController)
         toss/ TossPaymentClient(인터페이스, 테스트 stub용) + Impl(RestClient), DTO, 예외
```
- 화면(Thymeleaf)은 `orders/checkout.html` **1개뿐**(+`static/js/checkout.js`). 상품목록/주문생성/성공/실패/취소는 전부 REST(JSON) 엔드포인트로 만들고 curl/브라우저 주소창으로 응답 데이터를 직접 확인
- Toss API: `POST /v1/payments/confirm`, `POST /v1/payments/{key}/cancel`, `GET /v1/payments/{key}`(재조회)

## 구현 순서
0. DB/키 설정 → 1. 엔티티 → 2. 상품조회/주문생성 REST API(재고차감 없음) → 3. Toss 클라이언트 → **4. 체크아웃 화면(유일한 SSR) + 승인/실패 콜백 REST API(재고차감+confirm 트랜잭션, 핵심)** → 5. 취소 REST API(명시적 재고 복구) → 6. 테스트(H2, `@MockitoBean TossPaymentClient`로 정상/금액불일치/재고부족/중복승인/동시성 검증)

## 최종 검증
`./gradlew test` + 수동 확인 6가지: 정상결제 / 실패결제 / 금액변조 / 재고소진 경합 / 중복클릭 / 취소. 체크아웃 화면 진입~실제 결제만 브라우저로, 나머지(주문생성/콜백결과/취소)는 curl 등으로 JSON 응답과 DB 상태를 대조해 확인

## 구현 시나리오

| # | 분류 | 시나리오 | 트리거 | 기대 결과 |
|---|---|---|---|---|
| 1 | 주문 생성 | 정상 주문 생성 | 재고 있는 상품 주문 | `Order(PENDING)` 생성, 금액 서버 계산·저장 (재고는 아직 미차감) |
| 2 | 정상 결제 | 결제 성공 | 위젯에서 결제 완료 → successUrl 콜백 | 금액 일치 확인 → 재고 조건부 차감 성공 → confirm 성공 → `Order=PAID`, `Payment` 생성 |
| 3 | 결제 실패 | 사용자/PG 실패 | 위젯에서 인증실패·취소 → failUrl 콜백 | `Order=FAILED` (재고는 애초에 안 건드렸으므로 영향 없음) |
| 4 | 결제 실패 | confirm 자체 거절 | 재고는 있으나 Toss가 `REJECT_CARD_PAYMENT` 등으로 거절 | 트랜잭션 롤백으로 차감분 자동 복구 + `Order=FAILED` (별도 트랜잭션 기록) |
| 5 | 정합성 방어 | 금액 위변조 | successUrl 콜백 쿼리의 amount ≠ 서버 저장 금액 | confirm 자체를 호출하지 않고 즉시 `Order=FAILED` |
| 6 | 정합성 방어 | 재고 소진 경합 | 마지막 재고를 두고 동시에 여러 결제 콜백 도착 | 하나만 조건부 UPDATE 성공 → confirm 진행/PAID, 나머지는 재고부족으로 confirm 호출 없이 FAILED |
| 7 | 정합성 방어 | 중복 승인(새로고침 등) | 이미 PAID인 주문에 success 콜백 재도달 | confirm 재호출 없이 기존 PAID 결과 그대로 반환(멱등) |
| 8 | 정합성 방어 | Toss `ALREADY_PROCESSED_PAYMENT` | 경합으로 두 요청이 모두 confirm 호출한 극단 케이스 | 에러 수신 시 `GET /v1/payments/{key}`로 재조회 후 실제 상태로 맞춤 |
| 9 | 결제 취소 | 결제완료 후 취소 | PAID 주문에 대해 취소 요청 | Toss cancel 성공 → `Order=CANCELED` + 재고 명시적 복구 |
| 10 | 이탈 | 콜백 자체가 안 옴 | 결제창 진입 후 브라우저 종료 등 | 재고 미차감 상태라 영향 없음, 주문은 PENDING으로 방치돼도 무해(범위 밖) |

구현 순서: 1 → 2 → 3/4 → 5~8 → 9. 각 시나리오는 그대로 테스트 케이스로 옮겨 검증한다.
