package com.threedfly.bff.dto.external.product;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for creating payments in Product Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    private Long orderId;
    private String method; // PAYPAL, STRIPE, etc.
    private BigDecimal totalAmount;
    private String currency = "USD";
    private String description;
    private String successUrl;
    private String cancelUrl;
    private Map<String, Object> providerData;
}
