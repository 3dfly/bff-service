package com.threedfly.bff.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity to track order processing logs
 */
@Entity
@Table(name = "order_processing_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderProcessingLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long customerId;
    
    @Column(nullable = false)
    private String productId;
    
    @Column(nullable = false)
    private BigDecimal totalAmount;
    
    @Column(nullable = false)
    private String status; // PROCESSING, COMPLETED, FAILED
    
    @Column(nullable = false)
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
