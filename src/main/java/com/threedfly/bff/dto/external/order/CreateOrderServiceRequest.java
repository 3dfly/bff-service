package com.threedfly.bff.dto.external.order;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for creating orders in Order Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderServiceRequest {
    private String productId;
    private Long supplierId;
    private Long customerId;
    private Long sellerId;
    private Integer quantity;
    private String stlFileUrl;
    private ShippingAddressDto shippingAddress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressDto {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }
}
