package com.threedfly.bff.dto.external.product;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for executing payments in Product Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutePaymentRequest {
    private String providerPaymentId;
    private String providerPayerId;
}
