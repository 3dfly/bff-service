package com.threedfly.bff.dto.external.order;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for updating order status
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {
    private String status;
}
