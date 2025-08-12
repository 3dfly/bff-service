package com.threedfly.bff.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BFF Order Response
 * 
 * Comprehensive response containing order details, supplier information,
 * payment status, and tracking information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    
    // Order Basic Information
    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private LocalDateTime orderDate;
    private LocalDateTime estimatedDeliveryDate;
    
    // Customer Information
    private CustomerInfo customer;
    
    // Product Information
    private ProductInfo product;
    
    // Supplier Information
    private SupplierInfo supplier;
    
    // Seller Information (if applicable)
    private SellerInfo seller;
    
    // Shipping Information
    private ShippingInfo shipping;
    
    // Payment Information
    private PaymentInfo payment;
    
    // Tracking Information
    private TrackingInfo tracking;
    
    // Pricing Breakdown
    private PricingBreakdown pricing;
    
    // Order Processing Steps
    private List<OrderProcessingStep> processingSteps;
    
    // Additional metadata
    private String orderNotes;
    private LocalDateTime lastUpdated;
    
    /**
     * Order Status Enum
     */
    public enum OrderStatus {
        PENDING,           // Order received, processing started
        SUPPLIER_FOUND,    // Closest supplier identified
        PAYMENT_PENDING,   // Payment being processed
        PAYMENT_COMPLETED, // Payment successful
        PAYMENT_FAILED,    // Payment failed
        CONFIRMED,         // Order confirmed and being prepared
        IN_PRODUCTION,     // Item being manufactured/prepared
        SHIPPED,           // Order shipped
        DELIVERED,         // Order delivered
        CANCELLED,         // Order cancelled
        REFUNDED          // Order refunded
    }
    
    /**
     * Customer Information
     */
    @Data
    @Builder
    public static class CustomerInfo {
        private Long customerId;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
    }
    
    /**
     * Product Information
     */
    @Data
    @Builder
    public static class ProductInfo {
        private String productId;
        private String name;
        private String description;
        private String category;
        private Integer quantity;
        private String stlFileUrl;
        private BigDecimal unitPrice;
    }
    
    /**
     * Supplier Information
     */
    @Data
    @Builder
    public static class SupplierInfo {
        private Long supplierId;
        private String name;
        private String address;
        private String city;
        private String state;
        private String country;
        private Double latitude;
        private Double longitude;
        private Double distanceFromCustomer; // in miles/km
        private String estimatedProductionTime;
        private Integer qualityRating; // 1-5 stars
    }
    
    /**
     * Seller Information
     */
    @Data
    @Builder
    public static class SellerInfo {
        private Long sellerId;
        private String businessName;
        private String contactEmail;
        private String contactPhone;
        private BigDecimal commissionRate;
    }
    
    /**
     * Shipping Information
     */
    @Data
    @Builder
    public static class ShippingInfo {
        private CreateOrderRequest.ShippingAddress address;
        private CreateOrderRequest.DeliverySpeed deliverySpeed;
        private BigDecimal shippingCost;
        private String carrierName;
        private String trackingNumber;
        private LocalDateTime shippedDate;
        private LocalDateTime estimatedDeliveryDate;
    }
    
    /**
     * Payment Information
     */
    @Data
    @Builder
    public static class PaymentInfo {
        private Long paymentId;
        private PaymentStatus status;
        private CreateOrderRequest.PaymentMethod method;
        private BigDecimal totalAmount;
        private String currency;
        private String transactionId;
        private String providerPaymentId;
        private LocalDateTime paymentDate;
        private String failureReason;
    }
    
    /**
     * Payment Status Enum
     */
    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
    
    /**
     * Tracking Information
     */
    @Data
    @Builder
    public static class TrackingInfo {
        private String trackingNumber;
        private String carrierName;
        private String trackingUrl;
        private List<TrackingEvent> events;
        private TrackingStatus currentStatus;
    }
    
    /**
     * Tracking Event
     */
    @Data
    @Builder
    public static class TrackingEvent {
        private LocalDateTime timestamp;
        private String status;
        private String description;
        private String location;
    }
    
    /**
     * Tracking Status
     */
    public enum TrackingStatus {
        LABEL_CREATED,
        PICKED_UP,
        IN_TRANSIT,
        OUT_FOR_DELIVERY,
        DELIVERED,
        DELIVERY_ATTEMPTED,
        EXCEPTION
    }
    
    /**
     * Pricing Breakdown
     */
    @Data
    @Builder
    public static class PricingBreakdown {
        private BigDecimal productCost;
        private BigDecimal shippingCost;
        private BigDecimal taxAmount;
        private BigDecimal platformFee;
        private BigDecimal sellerCommission;
        private BigDecimal supplierAmount;
        private BigDecimal totalAmount;
        private String currency;
    }
    
    /**
     * Order Processing Step
     */
    @Data
    @Builder
    public static class OrderProcessingStep {
        private String stepName;
        private StepStatus status;
        private LocalDateTime startTime;
        private LocalDateTime completedTime;
        private String description;
        private String errorMessage;
        private Integer durationMs;
    }
    
    /**
     * Step Status
     */
    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}
