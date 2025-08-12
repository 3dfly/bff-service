package com.threedfly.bff.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * Payment Method Specific Data
 * 
 * This class holds payment method specific information like:
 * - PayPal email
 * - Stripe card token
 * - Bank account details
 * - etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodData {
    
    // PayPal specific
    private String paypalEmail;
    
    // Stripe specific
    private String stripeCardToken;
    private String stripeCustomerId;
    
    // Bank transfer specific
    private String bankAccount;
    private String routingNumber;
    
    // Generic key-value pairs for other payment methods
    private Map<String, Object> additionalData;
}
