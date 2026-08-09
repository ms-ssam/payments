package org.example.payments.order;

import lombok.RequiredArgsConstructor;
import org.example.payments.common.AppProperties;
import org.example.payments.payment.toss.TossPaymentsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final AppProperties appProperties;

    // 주문을 생성한다
    @PostMapping
    @ResponseBody
    public ResponseEntity<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateOrderResponse.from(order));
    }

    // 결제위젯을 렌더링할 체크아웃 화면에 필요한 값을 채워 반환한다
    @GetMapping("/{orderId}/checkout")
    public String checkout(@PathVariable String orderId, Model model) {
        CheckoutInfo checkoutInfo = orderService.getCheckoutInfo(orderId);

        model.addAttribute("clientKey", tossPaymentsProperties.clientKey());
        model.addAttribute("customerKey", checkoutInfo.customerKey());
        model.addAttribute("orderId", checkoutInfo.orderId());
        model.addAttribute("orderName", checkoutInfo.orderName());
        model.addAttribute("amount", checkoutInfo.amount());
        model.addAttribute("successUrl", appProperties.baseUrl() + "/payments/success");
        model.addAttribute("failUrl", appProperties.baseUrl() + "/payments/fail");
        return "orders/checkout";
    }
}
