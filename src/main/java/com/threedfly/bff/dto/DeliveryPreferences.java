package com.threedfly.bff.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Delivery Preferences
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPreferences {
    
    private DeliverySpeed deliverySpeed;
    
    // Optional preferred delivery date
    private LocalDateTime preferredDeliveryDate;
    
    // Special delivery instructions
    private String deliveryInstructions;
    
    /**
     * Delivery speed options
     */
    public enum DeliverySpeed {
        STANDARD,    // 5-7 business days
        EXPEDITED,   // 2-3 business days
        OVERNIGHT    // Next business day
    }
}
