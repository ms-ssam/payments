# 결제 성공/실패 흐름 시퀀스 다이어그램 & 테스트 가이드

참여자: **브라우저(클라이언트)** / **서버(payments)** / **토스 서버**

## 1. 성공 흐름

```mermaid
sequenceDiagram
    autonumber
    participant B as 브라우저(클라이언트)
    participant S as 서버(payments)
    participant T as 토스 서버

    B->>S: POST /orders {productId, quantity}
    S->>S: OrderService.createOrder() - 재고 확인만, Order(PENDING) 저장
    S-->>B: 201 {orderId, orderName, amount}

    B->>S: GET /orders/{orderId}/checkout
    S->>S: OrderService.getCheckoutInfo()
    S-->>B: 200 checkout.html (clientKey, customerKey, orderId, amount, successUrl, failUrl)

    B->>T: 위젯 초기화/렌더 (renderPaymentMethods, renderAgreement)
    T-->>B: 결제수단 선택 UI

    B->>T: widgets.requestPayment() - 사용자가 결제창에서 인증 진행
    T-->>B: 인증 성공, paymentKey 발급 → successUrl로 리다이렉트

    B->>S: GET /payments/success?paymentKey&orderId&amount
    S->>S: PaymentService.confirmPayment() - 금액 대조, 재고 조건부 차감
    S->>T: POST /v1/payments/confirm {paymentKey, orderId, amount}
    T-->>S: 200 {status: DONE, ...}
    S->>S: order.markPaid(), Payment 저장
    S-->>B: 200 {orderId, status:"PAID", amount, failReason:null}
```

## 2. 실패 흐름

```mermaid
sequenceDiagram
    autonumber
    participant B as 브라우저(클라이언트)
    participant S as 서버(payments)
    participant T as 토스 서버

    Note over B,T: 주문 생성 ~ 체크아웃 화면 진입까지는 성공 흐름과 동일

    alt A. 위젯 단계에서 실패 (사용자 취소, 인증 실패 등)
        B->>T: widgets.requestPayment()
        T-->>B: 실패 → failUrl로 리다이렉트 (code, message, orderId)
        B->>S: GET /payments/fail?orderId&code&message
        S->>S: OrderService.markFailed(orderId, code) → Order=FAILED
        S-->>B: 200 {orderId, status:"FAILED", failReason:code}
    else B. successUrl까지 도달했지만 서버 confirm 단계에서 실패
        B->>T: widgets.requestPayment()
        T-->>B: 인증 성공 → successUrl로 리다이렉트 (paymentKey, orderId, amount)
        B->>S: GET /payments/success?paymentKey&orderId&amount

        alt B1. 금액 위변조 감지
            S->>S: clientAmount ≠ order.totalAmount
            S->>S: markFailed(AMOUNT_MISMATCH) - confirm 호출 안 함
            S-->>B: 409 {message: 금액 불일치}
        else B2. 재고 소진 (경합)
            S->>S: decreaseStock() 0 rows → 재고 부족
            S->>S: markFailed(OUT_OF_STOCK), 트랜잭션 롤백(재고 자동 원복)
            S-->>B: 409 {message: 재고 부족}
        else B3. 토스가 confirm 거절
            S->>T: POST /v1/payments/confirm
            T-->>S: 4xx {code, message} (예: NOT_FOUND_PAYMENT_SESSION)
            S->>S: markFailed(code), 트랜잭션 롤백(재고 자동 원복)
            S-->>B: 402 {code}: {message}
        end
    end
```

## 3. 취소(환불) 흐름

```mermaid
sequenceDiagram
    autonumber
    participant B as 브라우저(클라이언트)
    participant S as 서버(payments)
    participant T as 토스 서버

    B->>S: POST /payments/{orderId}/cancel {cancelReason}
    S->>S: PaymentService.cancelPayment() - PAID 상태인지 확인

    alt 주문이 PAID가 아님 (PENDING/FAILED/CANCELED)
        S-->>B: 409 {message: 이미 종결된 주문이라 처리할 수 없습니다}
    else 주문이 PAID
        S->>T: POST /v1/payments/{paymentKey}/cancel {cancelReason}
        alt 토스가 환불 거절
            T-->>S: 4xx {code, message}
            S-->>B: 402 {code}: {message} (로컬 상태는 그대로 — 환불이 실제로 안 됐으니 아무것도 바꾸지 않음)
        else 토스 환불 성공
            T-->>S: 200 {status: CANCELED, ...}
            S->>S: order.markCanceled(reason), payment.markCanceled()
            S->>S: increaseStock() - 재고 복구
            S-->>B: 200 {orderId, status:"CANCELED", amount, failReason:cancelReason}
        end
    end
```

## 4. 테스트 방법

공통: `./gradlew bootRun`으로 서버 기동(기본 8080), 아래 명령은 이 상태를 전제로 함.

### 성공 케이스

1. 주문 생성해서 `orderId` 확보
   ```bash
   curl -X POST localhost:8080/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
   ```
2. 브라우저에서 `http://localhost:8080/orders/{orderId}/checkout` 접속
3. 결제수단 선택 → "결제하기" 클릭 → 테스트 환경이므로 위젯이 안내하는 방식으로 결제 진행(실청구 없음)
4. 완료되면 `/payments/success`로 자동 이동, `{"status":"PAID", ...}` 응답 확인
5. DB에서 `orders.status = PAID`, `payment` 테이블에 새 row(`status = DONE`) 생성 확인

### 실패 케이스

**A. 위젯 단계 실패** — 체크아웃 화면에서 결제창을 띄운 뒤 끝까지 진행하지 않고 **취소/닫기**로 빠져나오기 → `/payments/fail`로 리다이렉트되며 `{"status":"FAILED", "failReason":"..."}` 확인

**B1. 금액 위변조** — 정상 주문 생성 후, `successUrl` 콜백을 흉내내되 `amount`만 다르게 직접 호출
```bash
curl "localhost:8080/payments/success?paymentKey=아무값&orderId={실제orderId}&amount=1"
# → 409, "결제 금액이 일치하지 않습니다"
```

**B2. 재고 소진 경합** — 주문 생성 후, 다른 주문이 먼저 재고를 가져간 상황을 DB에서 강제로 재현
```bash
mysql -uroot -p toss -e "UPDATE product SET stock_quantity=0 WHERE id={productId};"
curl "localhost:8080/payments/success?paymentKey=아무값&orderId={실제orderId}&amount={정상금액}"
# → 409, "재고가 부족합니다"
```
이 경우는 애초에 차감 자체가 안 일어나므로(조건부 UPDATE가 0건 갱신), 재고는 강제로 세팅해둔 값(0) 그대로 유지되는지만 확인하면 됨 — "롤백"이 일어나는 케이스는 아님(B3와 구분).

**B3. 토스 거절** — 정상 주문 생성 후, 존재하지 않는 `paymentKey`로 confirm 유도(실제 토스 서버가 거절 응답)
```bash
curl localhost:8080/products   # confirm 호출 전 재고 수를 먼저 기록해둔다
curl "localhost:8080/payments/success?paymentKey=존재하지않는키&orderId={실제orderId}&amount={정상금액}"
# → 402, 예: "NOT_FOUND_PAYMENT_SESSION: 결제 시간이 만료되어..."
curl localhost:8080/products   # 재고가 "호출 전과 동일한 값"으로 롤백됐는지 비교(재고 차감 → 토스 거절 → 트랜잭션 롤백)
```

### 취소(환불) 케이스

정상 케이스 — `PAID` 상태인 주문(위 성공 케이스로 만든 orderId)을 취소
```bash
curl -X POST localhost:8080/payments/{PAID상태orderId}/cancel -H "Content-Type: application/json" -d '{"cancelReason":"단순 변심"}'
# → 200, {"status":"CANCELED", ...}
```
DB에서 `orders.status = CANCELED`, `payment.status = CANCELED`, 재고가 결제 전 수량으로 복구됐는지 확인

상태 가드 케이스 — `PENDING`/`FAILED` 주문에 취소 시도
```bash
curl -X POST localhost:8080/payments/{PENDING상태orderId}/cancel -H "Content-Type: application/json" -d '{"cancelReason":"단순 변심"}'
# → 409, "이미 종결된 주문이라 처리할 수 없습니다"
```
