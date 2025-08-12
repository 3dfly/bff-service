package com.threedfly.bff.dto.external.product;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * Product information from Product Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {
    private String id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String currency;
    private Boolean isActive;
    private String imageUrl;
    private String stlFileUrl;
}
