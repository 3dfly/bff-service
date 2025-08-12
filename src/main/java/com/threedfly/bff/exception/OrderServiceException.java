package com.threedfly.bff.exception;

/**
 * Exception thrown when there's an error communicating with the Order Service
 */
public class OrderServiceException extends RuntimeException {
    
    public OrderServiceException(String message) {
        super(message);
    }
    
    public OrderServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
