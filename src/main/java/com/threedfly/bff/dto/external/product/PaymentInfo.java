package com.threedfly.bff.dto.external.product;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment information from Product Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {
    private Long id;
    private Long orderId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private BigDecimal platformFee;
    private BigDecimal sellerAmount;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    private String method; // PAYPAL, STRIPE, CREDIT_CARD, etc.
    private String providerPaymentId;
    private String providerPayerId;
    private String platformTransactionId;
    private String sellerTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String providerResponse;
}
