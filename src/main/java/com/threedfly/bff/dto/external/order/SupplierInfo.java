package com.threedfly.bff.dto.external.order;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Supplier information from Order Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierInfo {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String zipCode;
    private Double latitude;
    private Double longitude;
    private Double distanceFromCustomer; // in miles
    private String estimatedProductionTime;
    private Integer qualityRating; // 1-5 stars
    private Boolean isActive;
    private String contactEmail;
    private String contactPhone;
}
