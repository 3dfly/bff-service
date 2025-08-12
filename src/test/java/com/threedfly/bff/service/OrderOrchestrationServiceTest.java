package com.threedfly.bff.service;

import com.threedfly.bff.dto.CreateOrderRequest;
import com.threedfly.bff.dto.OrderResponse;
import com.threedfly.bff.dto.external.order.*;
import com.threedfly.bff.dto.external.product.*;
import com.threedfly.bff.entity.OrderProcessingLog;
import com.threedfly.bff.repository.OrderProcessingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOrchestrationServiceTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderProcessingLogRepository processingLogRepository;

    @InjectMocks
    private OrderOrchestrationService orderOrchestrationService;

    private CreateOrderRequest testRequest;
    private SupplierInfo testSupplier;
    private OrderInfo testOrder;
    private PaymentInfo testPayment;

    @BeforeEach
    void setUp() {
        // Create test request
        testRequest = new CreateOrderRequest();
        testRequest.setCustomerId(1L);
        testRequest.setCustomerEmail("test@example.com");
        testRequest.setProductId("test-product-001");
        testRequest.setQuantity(1);

        // Shipping address
        CreateOrderRequest.ShippingAddress shippingAddress = new CreateOrderRequest.ShippingAddress();
        shippingAddress.setFirstName("John");
        shippingAddress.setLastName("Doe");
        shippingAddress.setStreet("123 Main St");
        shippingAddress.setCity("San Francisco");
        shippingAddress.setState("CA");
        shippingAddress.setZipCode("94105");
        shippingAddress.setCountry("United States");
        shippingAddress.setLatitude(37.7749);
        shippingAddress.setLongitude(-122.4194);
        testRequest.setShippingAddress(shippingAddress);

        // Payment information
        CreateOrderRequest.PaymentInformation paymentInfo = new CreateOrderRequest.PaymentInformation();
        paymentInfo.setMethod(CreateOrderRequest.PaymentMethod.PAYPAL);
        paymentInfo.setTotalAmount(new BigDecimal("50.00"));
        paymentInfo.setCurrency("USD");
        paymentInfo.setDescription("Test payment");
        paymentInfo.setSuccessUrl("https://example.com/success");
        paymentInfo.setCancelUrl("https://example.com/cancel");
        testRequest.setPaymentInformation(paymentInfo);

        // Create test supplier
        testSupplier = new SupplierInfo();
        testSupplier.setId(1L);
        testSupplier.setName("Test Supplier");
        testSupplier.setDistanceFromCustomer(5.2);

        // Create test order
        testOrder = new OrderInfo();
        testOrder.setId(1L);
        testOrder.setProductId("test-product-001");
        testOrder.setSupplierId(1L);
        testOrder.setCustomerId(1L);
        testOrder.setStatus("PENDING");

        // Create test payment
        testPayment = new PaymentInfo();
        testPayment.setId(1L);
        testPayment.setOrderId(1L);
        testPayment.setTotalAmount(new BigDecimal("50.00"));
        testPayment.setPlatformFee(new BigDecimal("5.00"));
        testPayment.setSellerAmount(new BigDecimal("45.00"));
        testPayment.setStatus("COMPLETED");
        testPayment.setMethod("PAYPAL");
        testPayment.setProviderPaymentId("test-payment-123");
    }

    @Test
    void processOrder_Success() {
        // Arrange
        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(testSupplier));

        when(orderServiceClient.createOrder(any(CreateOrderServiceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testOrder));

        when(productServiceClient.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        when(productServiceClient.executePayment(anyString(), any(ExecutePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        // Act
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        OrderResponse response = result.join();

        // Assert
        assertNotNull(response);
        assertEquals(testOrder.getId(), response.getOrderId());
        assertEquals(OrderResponse.OrderStatus.PAYMENT_COMPLETED, response.getStatus());
        assertNotNull(response.getCustomer());
        assertEquals("test@example.com", response.getCustomer().getEmail());
        assertNotNull(response.getPayment());
        assertEquals(OrderResponse.PaymentStatus.COMPLETED, response.getPayment().getStatus());
        assertNotNull(response.getProcessingSteps());
        assertEquals(4, response.getProcessingSteps().size()); // 4 steps in the process

        // Verify all steps completed successfully
        for (OrderResponse.OrderProcessingStep step : response.getProcessingSteps()) {
            assertEquals(OrderResponse.StepStatus.COMPLETED, step.getStatus());
        }

        // Verify interactions
        verify(orderServiceClient).findClosestSupplier("test-product-001", 37.7749, -122.4194);
        verify(orderServiceClient).createOrder(any(CreateOrderServiceRequest.class));
        verify(productServiceClient).createPayment(any(CreatePaymentRequest.class));
        verify(productServiceClient).executePayment(eq("test-payment-123"), any(ExecutePaymentRequest.class));
        verify(processingLogRepository, times(2)).save(any(OrderProcessingLog.class));
    }

    @Test
    void processOrder_SupplierNotFound() {
        // Arrange
        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.failedFuture(
                        new OrderServiceClient.SupplierNotFoundException("No suppliers found")));

        // Act & Assert
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        
        assertThrows(Exception.class, result::join);

        // Verify only supplier search was attempted
        verify(orderServiceClient).findClosestSupplier("test-product-001", 37.7749, -122.4194);
        verify(orderServiceClient, never()).createOrder(any());
        verify(productServiceClient, never()).createPayment(any());
        verify(productServiceClient, never()).executePayment(anyString(), any());
    }

    @Test
    void processOrder_PaymentFailed() {
        // Arrange
        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(testSupplier));

        when(orderServiceClient.createOrder(any(CreateOrderServiceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testOrder));

        when(productServiceClient.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        when(productServiceClient.executePayment(anyString(), any(ExecutePaymentRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new ProductServiceClient.ProductServiceException("Payment execution failed")));

        // Act & Assert
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        
        assertThrows(Exception.class, result::join);

        // Verify all steps up to payment execution were attempted
        verify(orderServiceClient).findClosestSupplier(anyString(), anyDouble(), anyDouble());
        verify(orderServiceClient).createOrder(any(CreateOrderServiceRequest.class));
        verify(productServiceClient).createPayment(any(CreatePaymentRequest.class));
        verify(productServiceClient).executePayment(anyString(), any(ExecutePaymentRequest.class));
    }

    @Test
    void processOrder_WithDeliveryPreferences() {
        // Arrange
        CreateOrderRequest.DeliveryPreferences deliveryPrefs = new CreateOrderRequest.DeliveryPreferences();
        deliveryPrefs.setDeliverySpeed(CreateOrderRequest.DeliverySpeed.EXPEDITED);
        testRequest.setDeliveryPreferences(deliveryPrefs);

        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(testSupplier));

        when(orderServiceClient.createOrder(any(CreateOrderServiceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testOrder));

        when(productServiceClient.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        when(productServiceClient.executePayment(anyString(), any(ExecutePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        // Act
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        OrderResponse response = result.join();

        // Assert
        assertNotNull(response.getShipping());
        assertEquals(CreateOrderRequest.DeliverySpeed.EXPEDITED, response.getShipping().getDeliverySpeed());
        
        // Estimated delivery should be 3 days for expedited
        LocalDateTime expectedDelivery = LocalDateTime.now().plusDays(3);
        LocalDateTime actualDelivery = response.getShipping().getEstimatedDeliveryDate();
        assertTrue(actualDelivery.isAfter(expectedDelivery.minusMinutes(1)));
        assertTrue(actualDelivery.isBefore(expectedDelivery.plusMinutes(1)));
    }

    @Test
    void processOrder_DefaultDeliverySpeed() {
        // Arrange - no delivery preferences set
        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(testSupplier));

        when(orderServiceClient.createOrder(any(CreateOrderServiceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testOrder));

        when(productServiceClient.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        when(productServiceClient.executePayment(anyString(), any(ExecutePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        // Act
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        OrderResponse response = result.join();

        // Assert
        assertNotNull(response.getShipping());
        assertEquals(CreateOrderRequest.DeliverySpeed.STANDARD, response.getShipping().getDeliverySpeed());
        
        // Estimated delivery should be 7 days for standard
        LocalDateTime expectedDelivery = LocalDateTime.now().plusDays(7);
        LocalDateTime actualDelivery = response.getShipping().getEstimatedDeliveryDate();
        assertTrue(actualDelivery.isAfter(expectedDelivery.minusMinutes(1)));
        assertTrue(actualDelivery.isBefore(expectedDelivery.plusMinutes(1)));
    }

    @Test
    void processOrder_VerifyPricingBreakdown() {
        // Arrange
        when(processingLogRepository.save(any(OrderProcessingLog.class)))
                .thenReturn(new OrderProcessingLog());

        when(orderServiceClient.findClosestSupplier(anyString(), anyDouble(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(testSupplier));

        when(orderServiceClient.createOrder(any(CreateOrderServiceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testOrder));

        when(productServiceClient.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        when(productServiceClient.executePayment(anyString(), any(ExecutePaymentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(testPayment));

        // Act
        CompletableFuture<OrderResponse> result = orderOrchestrationService.processOrder(testRequest);
        OrderResponse response = result.join();

        // Assert pricing breakdown
        assertNotNull(response.getPricing());
        assertEquals(new BigDecimal("50.00"), response.getPricing().getTotalAmount());
        assertEquals(new BigDecimal("5.00"), response.getPricing().getPlatformFee());
        assertEquals(new BigDecimal("45.00"), response.getPricing().getSupplierAmount());
        assertEquals(new BigDecimal("45.00"), response.getPricing().getProductCost());
        assertEquals("USD", response.getPricing().getCurrency());
    }
}
