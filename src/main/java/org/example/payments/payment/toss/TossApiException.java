package org.example.payments.payment.toss;

import org.springframework.http.HttpStatusCode;

public class TossApiException extends RuntimeException {

    private final String code;
    private final HttpStatusCode status;

    public TossApiException(String code, String message, HttpStatusCode status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
