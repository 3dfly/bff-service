package com.threedfly.bff.service;

import com.threedfly.bff.dto.CreateOrderRequest;
import com.threedfly.bff.dto.OrderResponse;
import com.threedfly.bff.dto.external.order.*;
import com.threedfly.bff.dto.external.product.*;
import com.threedfly.bff.entity.OrderProcessingLog;
import com.threedfly.bff.repository.OrderProcessingLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Order Orchestration Service
 * 
 * Orchestrates the complete order flow:
 * 1. Receive order information
 * 2. Find closest supplier
 * 3. Create payment
 * 4. Execute payment
 * 
 * Handles the coordination between order-service and product-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderOrchestrationService {

    private final OrderServiceClient orderServiceClient;
    private final ProductServiceClient productServiceClient;
    private final OrderProcessingLogRepository processingLogRepository;

    /**
     * Process complete order flow
     */
    @Transactional
    public CompletableFuture<OrderResponse> processOrder(CreateOrderRequest request) {
        log.info("🎯 Starting order orchestration for customer: {} and product: {}", 
                request.getCustomerId(), request.getProductId());

        OrderProcessingLog processingLog = createProcessingLog(request);
        List<OrderResponse.OrderProcessingStep> steps = new ArrayList<>();

        return findClosestSupplier(request, steps)
                .thenCompose(supplier -> createOrder(request, supplier, steps))
                .thenCompose(order -> createPayment(request, order, steps))
                .thenCompose(payment -> executePayment(request, payment, steps))
                .thenApply(payment -> buildOrderResponse(request, payment, steps))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        log.error("❌ Order orchestration failed", throwable);
                        processingLog.setStatus("FAILED");
                        processingLog.setErrorMessage(throwable.getMessage());
                        addFailedStep(steps, throwable);
                    } else {
                        log.info("✅ Order orchestration completed successfully: {}", response.getOrderId());
                        processingLog.setStatus("COMPLETED");
                    }
                    processingLog.setCompletedAt(LocalDateTime.now());
                    processingLogRepository.save(processingLog);
                });
    }

    /**
     * Step 1: Find closest supplier
     */
    private CompletableFuture<SupplierInfo> findClosestSupplier(CreateOrderRequest request, 
                                                               List<OrderResponse.OrderProcessingStep> steps) {
        LocalDateTime stepStart = LocalDateTime.now();
        log.info("🔍 Step 1: Finding closest supplier for product: {}", request.getProductId());

        // Use customer's shipping address coordinates, or geocode if needed
        double latitude = request.getShippingAddress().getLatitude() != null ? 
                request.getShippingAddress().getLatitude() : 40.7128; // Default to NYC
        double longitude = request.getShippingAddress().getLongitude() != null ? 
                request.getShippingAddress().getLongitude() : -74.0060; // Default to NYC

        return orderServiceClient.findClosestSupplier(request.getProductId(), latitude, longitude)
                .whenComplete((supplier, throwable) -> {
                    LocalDateTime stepEnd = LocalDateTime.now();
                    if (throwable != null) {
                        steps.add(createFailedStep("Find Closest Supplier", stepStart, stepEnd, throwable.getMessage()));
                    } else {
                        steps.add(createCompletedStep("Find Closest Supplier", stepStart, stepEnd, 
                                "Found supplier: " + supplier.getName() + " (Distance: " + supplier.getDistanceFromCustomer() + " miles)"));
                    }
                });
    }

    /**
     * Step 2: Create order
     */
    private CompletableFuture<OrderInfo> createOrder(CreateOrderRequest request, SupplierInfo supplier, 
                                                    List<OrderResponse.OrderProcessingStep> steps) {
        LocalDateTime stepStart = LocalDateTime.now();
        log.info("📦 Step 2: Creating order with supplier: {}", supplier.getName());

        CreateOrderServiceRequest orderRequest = new CreateOrderServiceRequest();
        orderRequest.setProductId(request.getProductId());
        orderRequest.setSupplierId(supplier.getId());
        orderRequest.setCustomerId(request.getCustomerId());
        orderRequest.setSellerId(request.getPreferredSellerId());
        orderRequest.setQuantity(request.getQuantity());
        orderRequest.setStlFileUrl(request.getStlFileUrl());

        // Convert shipping address
        CreateOrderServiceRequest.ShippingAddressDto shippingAddress = new CreateOrderServiceRequest.ShippingAddressDto();
        shippingAddress.setStreet(request.getShippingAddress().getStreet());
        shippingAddress.setCity(request.getShippingAddress().getCity());
        shippingAddress.setState(request.getShippingAddress().getState());
        shippingAddress.setZipCode(request.getShippingAddress().getZipCode());
        shippingAddress.setCountry(request.getShippingAddress().getCountry());
        orderRequest.setShippingAddress(shippingAddress);

        return orderServiceClient.createOrder(orderRequest)
                .whenComplete((order, throwable) -> {
                    LocalDateTime stepEnd = LocalDateTime.now();
                    if (throwable != null) {
                        steps.add(createFailedStep("Create Order", stepStart, stepEnd, throwable.getMessage()));
                    } else {
                        steps.add(createCompletedStep("Create Order", stepStart, stepEnd, 
                                "Order created with ID: " + order.getId()));
                    }
                });
    }

    /**
     * Step 3: Create payment
     */
    private CompletableFuture<PaymentInfo> createPayment(CreateOrderRequest request, OrderInfo order, 
                                                        List<OrderResponse.OrderProcessingStep> steps) {
        LocalDateTime stepStart = LocalDateTime.now();
        log.info("💳 Step 3: Creating payment for order: {}", order.getId());

        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(order.getId());
        paymentRequest.setMethod(request.getPaymentInformation().getMethod().name());
        paymentRequest.setTotalAmount(request.getPaymentInformation().getTotalAmount());
        paymentRequest.setCurrency(request.getPaymentInformation().getCurrency());
        paymentRequest.setDescription(request.getPaymentInformation().getDescription());
        paymentRequest.setSuccessUrl(request.getPaymentInformation().getSuccessUrl());
        paymentRequest.setCancelUrl(request.getPaymentInformation().getCancelUrl());

        // Add payment method specific data
        Map<String, Object> providerData = new HashMap<>();
        if (request.getPaymentInformation().getPaymentMethodData() != null) {
            CreateOrderRequest.PaymentMethodData methodData = request.getPaymentInformation().getPaymentMethodData();
            if (methodData.getPaypalEmail() != null) {
                providerData.put("email", methodData.getPaypalEmail());
            }
            if (methodData.getCardToken() != null) {
                providerData.put("card_token", methodData.getCardToken());
            }
            if (methodData.getAdditionalData() != null) {
                providerData.putAll(methodData.getAdditionalData());
            }
        }
        paymentRequest.setProviderData(providerData);

        return productServiceClient.createPayment(paymentRequest)
                .whenComplete((payment, throwable) -> {
                    LocalDateTime stepEnd = LocalDateTime.now();
                    if (throwable != null) {
                        steps.add(createFailedStep("Create Payment", stepStart, stepEnd, throwable.getMessage()));
                    } else {
                        steps.add(createCompletedStep("Create Payment", stepStart, stepEnd, 
                                "Payment created with ID: " + payment.getId()));
                    }
                });
    }

    /**
     * Step 4: Execute payment
     */
    private CompletableFuture<PaymentInfo> executePayment(CreateOrderRequest request, PaymentInfo payment, 
                                                         List<OrderResponse.OrderProcessingStep> steps) {
        LocalDateTime stepStart = LocalDateTime.now();
        log.info("✅ Step 4: Executing payment: {}", payment.getId());

        ExecutePaymentRequest executeRequest = new ExecutePaymentRequest();
        executeRequest.setProviderPaymentId(payment.getProviderPaymentId());
        
        // Use customer ID as payer ID if not provided
        executeRequest.setProviderPayerId(request.getCustomerId().toString());

        return productServiceClient.executePayment(payment.getProviderPaymentId(), executeRequest)
                .whenComplete((executedPayment, throwable) -> {
                    LocalDateTime stepEnd = LocalDateTime.now();
                    if (throwable != null) {
                        steps.add(createFailedStep("Execute Payment", stepStart, stepEnd, throwable.getMessage()));
                    } else {
                        steps.add(createCompletedStep("Execute Payment", stepStart, stepEnd, 
                                "Payment executed successfully. Status: " + executedPayment.getStatus()));
                    }
                });
    }

    /**
     * Build the final order response
     */
    private OrderResponse buildOrderResponse(CreateOrderRequest request, PaymentInfo payment, 
                                           List<OrderResponse.OrderProcessingStep> steps) {
        return OrderResponse.builder()
                .orderId(payment.getOrderId())
                .orderNumber("ORD-" + payment.getOrderId())
                .status(mapPaymentStatusToOrderStatus(payment.getStatus()))
                .orderDate(LocalDateTime.now())
                .estimatedDeliveryDate(calculateEstimatedDeliveryDate(request))
                .customer(buildCustomerInfo(request))
                .product(buildProductInfo(request))
                .shipping(buildShippingInfo(request))
                .payment(buildPaymentInfo(payment))
                .pricing(buildPricingBreakdown(payment))
                .processingSteps(steps)
                .orderNotes(request.getOrderNotes())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // Helper methods

    private OrderProcessingLog createProcessingLog(CreateOrderRequest request) {
        OrderProcessingLog log = new OrderProcessingLog();
        log.setCustomerId(request.getCustomerId());
        log.setProductId(request.getProductId());
        log.setTotalAmount(request.getPaymentInformation().getTotalAmount());
        log.setStatus("PROCESSING");
        log.setStartedAt(LocalDateTime.now());
        return processingLogRepository.save(log);
    }

    private OrderResponse.OrderProcessingStep createCompletedStep(String stepName, LocalDateTime start, 
                                                                 LocalDateTime end, String description) {
        return OrderResponse.OrderProcessingStep.builder()
                .stepName(stepName)
                .status(OrderResponse.StepStatus.COMPLETED)
                .startTime(start)
                .completedTime(end)
                .description(description)
                .durationMs((int) java.time.Duration.between(start, end).toMillis())
                .build();
    }

    private OrderResponse.OrderProcessingStep createFailedStep(String stepName, LocalDateTime start, 
                                                              LocalDateTime end, String errorMessage) {
        return OrderResponse.OrderProcessingStep.builder()
                .stepName(stepName)
                .status(OrderResponse.StepStatus.FAILED)
                .startTime(start)
                .completedTime(end)
                .errorMessage(errorMessage)
                .durationMs((int) java.time.Duration.between(start, end).toMillis())
                .build();
    }

    private void addFailedStep(List<OrderResponse.OrderProcessingStep> steps, Throwable throwable) {
        LocalDateTime now = LocalDateTime.now();
        steps.add(OrderResponse.OrderProcessingStep.builder()
                .stepName("Order Processing")
                .status(OrderResponse.StepStatus.FAILED)
                .startTime(now)
                .completedTime(now)
                .errorMessage(throwable.getMessage())
                .durationMs(0)
                .build());
    }

    private OrderResponse.OrderStatus mapPaymentStatusToOrderStatus(String paymentStatus) {
        return switch (paymentStatus) {
            case "PENDING", "PROCESSING" -> OrderResponse.OrderStatus.PAYMENT_PENDING;
            case "COMPLETED" -> OrderResponse.OrderStatus.PAYMENT_COMPLETED;
            case "FAILED" -> OrderResponse.OrderStatus.PAYMENT_FAILED;
            case "CANCELLED" -> OrderResponse.OrderStatus.CANCELLED;
            default -> OrderResponse.OrderStatus.PENDING;
        };
    }

    private LocalDateTime calculateEstimatedDeliveryDate(CreateOrderRequest request) {
        int daysToAdd = switch (request.getDeliveryPreferences() != null ? 
                request.getDeliveryPreferences().getDeliverySpeed() : 
                CreateOrderRequest.DeliverySpeed.STANDARD) {
            case OVERNIGHT -> 1;
            case EXPEDITED -> 3;
            case SAME_DAY -> 0;
            default -> 7;
        };
        return LocalDateTime.now().plusDays(daysToAdd);
    }

    private OrderResponse.CustomerInfo buildCustomerInfo(CreateOrderRequest request) {
        return OrderResponse.CustomerInfo.builder()
                .customerId(request.getCustomerId())
                .email(request.getCustomerEmail())
                .firstName(request.getShippingAddress().getFirstName())
                .lastName(request.getShippingAddress().getLastName())
                .phone(request.getShippingAddress().getPhone())
                .build();
    }

    private OrderResponse.ProductInfo buildProductInfo(CreateOrderRequest request) {
        return OrderResponse.ProductInfo.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .stlFileUrl(request.getStlFileUrl())
                .unitPrice(request.getPaymentInformation().getTotalAmount()) // Simplified
                .build();
    }

    private OrderResponse.ShippingInfo buildShippingInfo(CreateOrderRequest request) {
        return OrderResponse.ShippingInfo.builder()
                .address(request.getShippingAddress())
                .deliverySpeed(request.getDeliveryPreferences() != null ? 
                        request.getDeliveryPreferences().getDeliverySpeed() : 
                        CreateOrderRequest.DeliverySpeed.STANDARD)
                .estimatedDeliveryDate(calculateEstimatedDeliveryDate(request))
                .build();
    }

    private OrderResponse.PaymentInfo buildPaymentInfo(PaymentInfo payment) {
        return OrderResponse.PaymentInfo.builder()
                .paymentId(payment.getId())
                .status(mapStringToPaymentStatus(payment.getStatus()))
                .method(CreateOrderRequest.PaymentMethod.valueOf(payment.getMethod()))
                .totalAmount(payment.getTotalAmount())
                .currency("USD")
                .transactionId(payment.getPlatformTransactionId())
                .providerPaymentId(payment.getProviderPaymentId())
                .paymentDate(payment.getCompletedAt())
                .failureReason(payment.getErrorMessage())
                .build();
    }

    private OrderResponse.PaymentStatus mapStringToPaymentStatus(String status) {
        return switch (status) {
            case "PENDING" -> OrderResponse.PaymentStatus.PENDING;
            case "PROCESSING" -> OrderResponse.PaymentStatus.PROCESSING;
            case "COMPLETED" -> OrderResponse.PaymentStatus.COMPLETED;
            case "FAILED" -> OrderResponse.PaymentStatus.FAILED;
            case "CANCELLED" -> OrderResponse.PaymentStatus.CANCELLED;
            default -> OrderResponse.PaymentStatus.PENDING;
        };
    }

    private OrderResponse.PricingBreakdown buildPricingBreakdown(PaymentInfo payment) {
        return OrderResponse.PricingBreakdown.builder()
                .productCost(payment.getTotalAmount().subtract(payment.getPlatformFee()))
                .platformFee(payment.getPlatformFee())
                .supplierAmount(payment.getSellerAmount())
                .totalAmount(payment.getTotalAmount())
                .currency("USD")
                .build();
    }
}
