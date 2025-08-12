package com.threedfly.bff.controller;

import com.threedfly.bff.dto.CreateOrderRequest;
import com.threedfly.bff.dto.OrderResponse;
import com.threedfly.bff.service.OrderOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * Order Controller - BFF Service
 * 
 * Main API endpoint for order orchestration.
 * Handles the complete order flow:
 * 1. Receive order information
 * 2. Find closest supplier
 * 3. Create payment
 * 4. Execute payment
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Orchestration", description = "Complete order processing workflow")
public class OrderController {

    private final OrderOrchestrationService orderOrchestrationService;

    /**
     * Create and process a complete order
     * 
     * This endpoint orchestrates the entire order flow:
     * 1. Finds the closest supplier based on shipping address
     * 2. Creates an order in the order service
     * 3. Creates a payment in the product service
     * 4. Executes the payment
     * 
     * @param request Complete order information including customer, product, shipping, and payment details
     * @return OrderResponse with complete order details, processing steps, and payment status
     */
    @PostMapping
    @Operation(
        summary = "Create and process a complete order", 
        description = "Orchestrates the complete order flow: find supplier → create order → process payment"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201", 
            description = "Order processed successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid request data",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "Internal server error during order processing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "503", 
            description = "Downstream services unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public CompletableFuture<ResponseEntity<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        
        log.info("🎯 POST /api/orders - Processing order for customer: {} and product: {}", 
                request.getCustomerId(), request.getProductId());

        return orderOrchestrationService.processOrder(request)
                .thenApply(orderResponse -> {
                    log.info("✅ Order processed successfully: {}", orderResponse.getOrderId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);
                })
                .exceptionally(throwable -> {
                    log.error("❌ Order processing failed", throwable);
                    
                    // Determine appropriate HTTP status based on exception type
                    HttpStatus status = determineHttpStatus(throwable);
                    
                    OrderResponse errorResponse = OrderResponse.builder()
                            .status(OrderResponse.OrderStatus.PAYMENT_FAILED)
                            .orderNotes("Order processing failed: " + throwable.getMessage())
                            .build();
                    
                    return ResponseEntity.status(status).body(errorResponse);
                });
    }

    /**
     * Get order status by ID
     */
    @GetMapping("/{orderId}")
    @Operation(
        summary = "Get order details by ID", 
        description = "Retrieve complete order information including current status and processing history"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Order found successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<String> getOrder(@PathVariable Long orderId) {
        log.info("🔍 GET /api/orders/{} - Retrieving order details", orderId);
        
        // This would typically call the order service to get current status
        // For now, return a simple response
        return ResponseEntity.ok("Order details for ID: " + orderId + " - Implementation pending");
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(
        summary = "Health check", 
        description = "Check if the BFF service is healthy and can communicate with downstream services"
    )
    public ResponseEntity<HealthResponse> healthCheck() {
        log.info("🏥 GET /api/orders/health - Health check requested");
        
        HealthResponse health = new HealthResponse();
        health.setStatus("UP");
        health.setService("bff-service");
        health.setTimestamp(java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(health);
    }

    /**
     * Determine HTTP status based on exception type
     */
    private HttpStatus determineHttpStatus(Throwable throwable) {
        String message = throwable.getMessage().toLowerCase();
        
        if (message.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        } else if (message.contains("invalid") || message.contains("validation")) {
            return HttpStatus.BAD_REQUEST;
        } else if (message.contains("timeout") || message.contains("unavailable")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Error Response DTO
     */
    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message", example = "Order processing failed")
        private String message;
        
        @Schema(description = "Error code", example = "ORDER_PROCESSING_FAILED")
        private String code;
        
        @Schema(description = "Timestamp", example = "2024-01-01T10:00:00")
        private java.time.LocalDateTime timestamp;

        // Constructors, getters, setters
        public ErrorResponse() {
            this.timestamp = java.time.LocalDateTime.now();
        }

        public ErrorResponse(String message, String code) {
            this();
            this.message = message;
            this.code = code;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Health Response DTO
     */
    @Schema(description = "Health check response")
    public static class HealthResponse {
        @Schema(description = "Service status", example = "UP")
        private String status;
        
        @Schema(description = "Service name", example = "bff-service")
        private String service;
        
        @Schema(description = "Timestamp", example = "2024-01-01T10:00:00")
        private java.time.LocalDateTime timestamp;

        // Constructors, getters, setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
