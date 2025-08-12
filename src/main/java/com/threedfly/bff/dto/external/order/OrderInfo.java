package com.threedfly.bff.dto.external.order;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Order information from Order Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {
    private Long id;
    private String productId;
    private Long supplierId;
    private Long customerId;
    private Long sellerId;
    private Integer quantity;
    private String stlFileUrl;
    private String shippingAddress; // JSON string
    private LocalDateTime orderDate;
    private String status; // PENDING, ACCEPTED, PROCESSING, SENT, CANCELLED
}
