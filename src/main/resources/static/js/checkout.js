const container = document.getElementById("checkout");
const clientKey = container.dataset.clientKey;
const customerKey = container.dataset.customerKey;
const orderId = container.dataset.orderId;
const orderName = container.dataset.orderName;
const amount = Number(container.dataset.amount);
const successUrl = container.dataset.successUrl;
const failUrl = container.dataset.failUrl;

const tossPayments = TossPayments(clientKey);
const widgets = tossPayments.widgets({ customerKey });

async function renderWidgets() {
    await widgets.setAmount({ value: amount, currency: "KRW" });
    await widgets.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" });
    await widgets.renderAgreement({ selector: "#agreement", variantKey: "DEFAULT" });
}

renderWidgets();

document.getElementById("pay-button").addEventListener("click", async () => {
    try {
        await widgets.requestPayment({
            orderId,
            orderName,
            successUrl,
            failUrl
        });
    } catch (error) {
        // 결제가 토스 서버에 정식 접수되기 전 취소(USER_CANCEL 등)는 failUrl로 리다이렉트되지 않고
        // 여기서 reject된다. 주문을 실패 처리하지 않고 재시도할 수 있게 안내만 한다.
        alert("결제가 취소됐어요. 다시 시도해주세요.");
    }
});

document.getElementById("cancel-button").addEventListener("click", () => {
    const confirmed = confirm("취소하시겠습니까? 취소하시면 다시 주문하셔야 합니다.");
    if (!confirmed) {
        return;
    }
    const params = new URLSearchParams({
        orderId,
        code: "USER_CANCELED_BY_UI",
        message: "사용자가 결제를 취소했습니다"
    });
    window.location.href = `${failUrl}?${params.toString()}`;
});
