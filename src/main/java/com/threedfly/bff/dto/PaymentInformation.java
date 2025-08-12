package com.threedfly.bff.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * Payment Information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInformation {
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod method;
    
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "10000.00", message = "Amount cannot exceed $10,000")
    private BigDecimal totalAmount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    private String currency;
    
    @NotBlank(message = "Payment description is required")
    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;
    
    // Payment method specific data (e.g., PayPal email, card token, etc.)
    private PaymentMethodData paymentMethodData;
    
    @NotBlank(message = "Success URL is required")
    private String successUrl;
    
    @NotBlank(message = "Cancel URL is required")
    private String cancelUrl;
    
    /**
     * Supported payment methods
     */
    public enum PaymentMethod {
        PAYPAL,
        STRIPE,
        CREDIT_CARD,
        BANK_TRANSFER,
        APPLE_PAY,
        GOOGLE_PAY
    }
}
