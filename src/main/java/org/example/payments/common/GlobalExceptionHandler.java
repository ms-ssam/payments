package org.example.payments.common;

import org.example.payments.product.InsufficientStockException;
import org.example.payments.product.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleConflict(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }
}
