package org.example.payments.payment;

import lombok.RequiredArgsConstructor;
import org.example.payments.order.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;

    // successUrl 콜백을 받아 결제 승인을 시도하고 결과를 반환한다
    @GetMapping("/success")
    public PaymentResultResponse success(@RequestParam String paymentKey, @RequestParam String orderId,
                                          @RequestParam Long amount) {
        return PaymentResultResponse.from(paymentService.confirmPayment(orderId, paymentKey, amount));
    }

    // failUrl 콜백을 받아 주문을 실패 처리하고 결과를 반환한다
    @GetMapping("/fail")
    public PaymentResultResponse fail(@RequestParam String orderId,
                                       @RequestParam(required = false) String code,
                                       @RequestParam(required = false) String message) {
        orderService.markFailed(orderId, code != null ? code : "UNKNOWN");
        return PaymentResultResponse.from(orderService.getOrder(orderId));
    }
}
