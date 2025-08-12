package com.threedfly.bff.exception;

/**
 * Exception thrown when no suppliers are found for a given product and location
 */
public class SupplierNotFoundException extends RuntimeException {
    
    public SupplierNotFoundException(String message) {
        super(message);
    }
    
    public SupplierNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
