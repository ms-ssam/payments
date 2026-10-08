# payments

토스페이먼츠 결제위젯(v2)을 연동해 **결제 → 취소 → 구매확정 → 정산 → 원장 기록**까지 결제 도메인의 흐름을 직접 구현해 본 학습용 토이 프로젝트입니다.

화면은 위젯을 띄우는 체크아웃 페이지 1개뿐이고, 나머지는 모두 REST API(JSON)로 확인합니다.

## 기술 스택

Java 21 · Spring Boot 4.1 · Spring Data JPA · MySQL · Thymeleaf · RestClient · Toss Payments 결제위젯 v2 (테스트 키)

## 구현한 것

| 영역 | 내용 |
|---|---|
| 주문·결제 | 상품 조회, 주문 생성, 위젯 결제, 승인(confirm)/실패 콜백 처리 |
| 취소(환불) | 결제 완료 건 취소 → Toss cancel 호출 + 재고 복구 |
| 선수금 | 결제 승인 시점엔 **선수금(부채)**, 구매확정 시점에 **매출**로 전환 |
| 정산 | PG 정산을 시뮬레이션하는 배치(수수료 차감), 정산 후 취소분은 **환수(clawback)** 해 다음 정산에서 상계 |
| 원장 | **복식부기** 원장. 모든 분개는 `차변 합 = 대변 합` 검증을 통과해야 저장 |
| 미수금 | 결제됐지만 아직 정산되지 않은 금액을 원장 잔액으로 조회 |

## 흐름 한눈에 보기

```
주문 생성(PENDING) ─▶ 위젯 결제 ─▶ 승인(PAID) ─┬─▶ 구매확정(CONFIRMED)   선수금 → 매출
                                              └─▶ 취소(CANCELED)        재고 복구, 정산 후라면 환수
정산 배치: 미수금 → 현금 + 수수료 (대기 중인 환수분 상계)
```

## API

| Method | Path | 설명 |
|---|---|---|
| GET | `/products` | 상품 목록 |
| POST | `/orders` | 주문 생성 `{productId, quantity}` |
| GET | `/orders/{orderId}/checkout` | 결제위젯 화면 (브라우저로 열어 결제) |
| POST | `/payments/{orderId}/cancel` | 결제 취소 |
| POST | `/payments/{orderId}/confirm` | 구매확정 |
| POST | `/settlements?date=` | 정산 배치 실행 |
| GET | `/settlements/{date}` | 정산 결과 조회 |
| GET | `/receivables` | 미수금(미정산금) 조회 |

## 실행

1. 로컬 MySQL에 `toss` 데이터베이스를 만들고, `src/main/resources/application.yml`의 접속 정보를 맞춥니다.
2. `./gradlew bootRun`
3. 기동하면 게스트 구매자 1명과 데모 상품 3개가 자동으로 들어갑니다.
4. `POST /orders`로 주문을 만들고, 브라우저에서 `http://localhost:8080/orders/{orderId}/checkout`을 열어 테스트 결제를 진행합니다.

## 범위에서 제외한 것

웹훅, 가상계좌·계좌이체, 다중 판매자(마켓플레이스), 자동화 테스트. 실제 정산은 PG가 계산해 알려주는 값이므로, 여기서는 고정 수수료율(`payments.settlement.fee-rate`)로 직접 계산해 시뮬레이션합니다.

## 문서

설계 배경, 시퀀스 다이어그램, 수동 테스트 가이드는 [`docs/`](docs)에 있습니다.

- [결제위젯 연동 계획](docs/payment-widget-integration-plan.md) · [결제/취소 흐름](docs/payment-flow-sequence.md)
- [선수금·정산·원장 계획](docs/settlement-ledger-plan.md) · [정산·원장 흐름](docs/settlement-ledger-flow-sequence.md)
