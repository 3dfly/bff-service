package com.threedfly.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threedfly.bff.dto.CreateOrderRequest;
import com.threedfly.bff.dto.OrderResponse;
import com.threedfly.bff.service.OrderOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderOrchestrationService orderOrchestrationService;

    @Autowired
    private ObjectMapper objectMapper;

    private CreateOrderRequest validRequest;
    private OrderResponse mockResponse;

    @BeforeEach
    void setUp() {
        // Create valid request
        validRequest = new CreateOrderRequest();
        validRequest.setCustomerId(1L);
        validRequest.setCustomerEmail("test@example.com");
        validRequest.setProductId("test-product-001");
        validRequest.setQuantity(1);

        // Shipping address
        CreateOrderRequest.ShippingAddress shippingAddress = new CreateOrderRequest.ShippingAddress();
        shippingAddress.setFirstName("John");
        shippingAddress.setLastName("Doe");
        shippingAddress.setStreet("123 Main St");
        shippingAddress.setCity("San Francisco");
        shippingAddress.setState("CA");
        shippingAddress.setZipCode("94105");
        shippingAddress.setCountry("United States");
        validRequest.setShippingAddress(shippingAddress);

        // Payment information
        CreateOrderRequest.PaymentInformation paymentInfo = new CreateOrderRequest.PaymentInformation();
        paymentInfo.setMethod(CreateOrderRequest.PaymentMethod.PAYPAL);
        paymentInfo.setTotalAmount(new BigDecimal("50.00"));
        paymentInfo.setCurrency("USD");
        paymentInfo.setDescription("Test payment");
        paymentInfo.setSuccessUrl("https://example.com/success");
        paymentInfo.setCancelUrl("https://example.com/cancel");
        validRequest.setPaymentInformation(paymentInfo);

        // Create mock response
        mockResponse = OrderResponse.builder()
                .orderId(1L)
                .orderNumber("ORD-1")
                .status(OrderResponse.OrderStatus.PAYMENT_COMPLETED)
                .orderDate(LocalDateTime.now())
                .customer(OrderResponse.CustomerInfo.builder()
                        .customerId(1L)
                        .email("test@example.com")
                        .firstName("John")
                        .lastName("Doe")
                        .build())
                .payment(OrderResponse.PaymentInfo.builder()
                        .paymentId(1L)
                        .status(OrderResponse.PaymentStatus.COMPLETED)
                        .totalAmount(new BigDecimal("50.00"))
                        .currency("USD")
                        .build())
                .build();
    }

    @Test
    void createOrder_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        when(orderOrchestrationService.processOrder(any(CreateOrderRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Act
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert async result
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PAYMENT_COMPLETED"))
                .andExpect(jsonPath("$.customer.email").value("test@example.com"))
                .andExpect(jsonPath("$.payment.status").value("COMPLETED"));
    }

    @Test
    void createOrder_ServiceFailure_ReturnsInternalServerError() throws Exception {
        // Arrange
        when(orderOrchestrationService.processOrder(any(CreateOrderRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service failure")));

        // Act
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert async result
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.orderNotes").exists());
    }

    @Test
    void createOrder_InvalidRequest_MissingCustomerId_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setCustomerId(null);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_InvalidRequest_InvalidEmail_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setCustomerEmail("invalid-email");

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_InvalidRequest_InvalidZipCode_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.getShippingAddress().setZipCode("invalid-zip");

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_EmptyBody_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_MalformedJson_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_MissingPaymentInfo_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setPaymentInformation(null);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_MissingShippingAddress_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setShippingAddress(null);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_InvalidQuantity_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setQuantity(0);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_NegativeAmount_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.getPaymentInformation().setTotalAmount(new BigDecimal("-10.00"));

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_ReturnsImplementationPending() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Implementation pending")));
    }

    @Test
    void healthCheck_ReturnsHealthStatus() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/orders/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("bff-service"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createOrder_WithDeliveryPreferences_ReturnsCreated() throws Exception {
        // Arrange
        CreateOrderRequest.DeliveryPreferences delivery = new CreateOrderRequest.DeliveryPreferences();
        delivery.setDeliverySpeed(CreateOrderRequest.DeliverySpeed.EXPEDITED);
        validRequest.setDeliveryPreferences(delivery);

        when(orderOrchestrationService.processOrder(any(CreateOrderRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Act
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert async result
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void createOrder_WithStlFileUrl_ReturnsCreated() throws Exception {
        // Arrange
        validRequest.setStlFileUrl("https://example.com/models/dragon.stl");

        when(orderOrchestrationService.processOrder(any(CreateOrderRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Act
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert async result
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void createOrder_WithOrderNotes_ReturnsCreated() throws Exception {
        // Arrange
        validRequest.setOrderNotes("Please use high-quality resin");

        when(orderOrchestrationService.processOrder(any(CreateOrderRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Act
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert async result
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
    }
}