package com.threedfly.bff.exception;

/**
 * Exception thrown when there's an error communicating with the Product Service
 */
public class ProductServiceException extends RuntimeException {
    
    public ProductServiceException(String message) {
        super(message);
    }
    
    public ProductServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
