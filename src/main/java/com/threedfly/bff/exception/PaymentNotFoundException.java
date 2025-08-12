package com.threedfly.bff.exception;

/**
 * Exception thrown when a payment cannot be found
 */
public class PaymentNotFoundException extends RuntimeException {
    
    public PaymentNotFoundException(String message) {
        super(message);
    }
    
    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
