package com.threedfly.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * BFF Create Order Request
 * 
 * This is the main request DTO for the order orchestration API.
 * It contains all information needed to:
 * 1. Find the closest supplier
 * 2. Create an order
 * 3. Process payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    // Customer Information
    @NotNull(message = "Customer ID is required")
    private Long customerId;
    
    @Email(message = "Valid customer email is required")
    @NotBlank(message = "Customer email is required")
    private String customerEmail;
    
    // Product Information
    @NotBlank(message = "Product ID is required")
    private String productId;
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100, message = "Quantity cannot exceed 100")
    private Integer quantity;
    
    // Optional STL file for custom 3D printing
    private String stlFileUrl;
    
    // Shipping Information
    @Valid
    @NotNull(message = "Shipping address is required")
    private ShippingAddress shippingAddress;
    
    // Payment Information
    @Valid
    @NotNull(message = "Payment information is required")
    private PaymentInformation paymentInformation;
    
    // Optional seller preference (if customer wants specific seller)
    private Long preferredSellerId;
    
    // Optional delivery preferences
    private DeliveryPreferences deliveryPreferences;
    
    // Order notes/special instructions
    @Size(max = 500, message = "Order notes cannot exceed 500 characters")
    private String orderNotes;
    
    /**
     * Shipping Address Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        private String firstName;
        
        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        private String lastName;
        
        @NotBlank(message = "Street address is required")
        @Size(max = 100, message = "Street cannot exceed 100 characters")
        private String street;
        
        @Size(max = 100, message = "Address line 2 cannot exceed 100 characters")
        private String street2;
        
        @NotBlank(message = "City is required")
        @Size(max = 50, message = "City cannot exceed 50 characters")
        private String city;
        
        @NotBlank(message = "State is required")
        @Size(max = 50, message = "State cannot exceed 50 characters")
        private String state;
        
        @NotBlank(message = "ZIP code is required")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Invalid ZIP code format")
        private String zipCode;
        
        @NotBlank(message = "Country is required")
        @Size(max = 50, message = "Country cannot exceed 50 characters")
        private String country;
        
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        private String phone;
        
        // Geographic coordinates for supplier distance calculation
        private Double latitude;
        private Double longitude;
    }
    
    /**
     * Payment Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInformation {
        @NotNull(message = "Payment method is required")
        private PaymentMethod method;
        
        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "10000.00", message = "Amount cannot exceed $10,000")
        private BigDecimal totalAmount;
        
        @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g., USD)")
        private String currency = "USD";
        
        @Size(max = 500, message = "Payment description cannot exceed 500 characters")
        private String description;
        
        // Payment method specific data
        private PaymentMethodData paymentMethodData;
        
        // URLs for redirect-based payments
        @Pattern(regexp = "^https?://.*", message = "Success URL must be a valid URL")
        private String successUrl;
        
        @Pattern(regexp = "^https?://.*", message = "Cancel URL must be a valid URL")
        private String cancelUrl;
    }
    
    /**
     * Payment Method Enum
     */
    public enum PaymentMethod {
        PAYPAL,
        STRIPE,
        CREDIT_CARD,
        BANK_TRANSFER,
        APPLE_PAY,
        GOOGLE_PAY,
        SHOPIFY
    }
    
    /**
     * Payment Method Specific Data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodData {
        // For PayPal
        private String paypalEmail;
        
        // For Stripe/Credit Card
        private String cardToken;
        private String cardLast4;
        private String cardBrand;
        
        // For Bank Transfer
        private String bankAccountNumber;
        private String routingNumber;
        
        // Generic key-value pairs for other payment methods
        private java.util.Map<String, Object> additionalData;
    }
    
    /**
     * Delivery Preferences
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryPreferences {
        @NotNull(message = "Delivery speed is required")
        private DeliverySpeed deliverySpeed;
        
        private java.time.LocalDate preferredDeliveryDate;
        
        @Size(max = 200, message = "Delivery instructions cannot exceed 200 characters")
        private String deliveryInstructions;
        
        private boolean signatureRequired = false;
        private boolean leaveAtDoor = false;
    }
    
    /**
     * Delivery Speed Options
     */
    public enum DeliverySpeed {
        STANDARD,     // 5-7 business days
        EXPEDITED,    // 2-3 business days  
        OVERNIGHT,    // Next business day
        SAME_DAY      // Same day delivery (if available)
    }
}
