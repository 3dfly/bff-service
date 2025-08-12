package com.threedfly.bff.service;

import com.threedfly.bff.dto.external.order.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with Order Service
 * 
 * Handles:
 * - Finding closest suppliers
 * - Creating orders
 * - Managing order lifecycle
 * 
 * Includes circuit breaker, retry, and timeout patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceClient {

    @Qualifier("orderServiceWebClient")
    private final WebClient orderServiceWebClient;

    /**
     * Find the closest supplier based on customer location
     */
    @CircuitBreaker(name = "order-service", fallbackMethod = "findClosestSupplierFallback")
    @Retry(name = "order-service")
    @TimeLimiter(name = "order-service")
    public CompletableFuture<SupplierInfo> findClosestSupplier(String productId, double latitude, double longitude) {
        log.info("🔍 Finding closest supplier for product: {} at location: {}, {}", productId, latitude, longitude);

        return orderServiceWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/suppliers/closest")
                        .queryParam("productId", productId)
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .build())
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, 
                         response -> Mono.error(new SupplierNotFoundException("No suppliers found for product: " + productId)))
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new OrderServiceException("Order service error: " + errorBody))))
                .bodyToMono(SupplierInfo.class)
                .doOnSuccess(supplier -> log.info("✅ Found closest supplier: {} (Distance: {} miles)", 
                        supplier.getName(), supplier.getDistanceFromCustomer()))
                .doOnError(error -> log.error("❌ Failed to find closest supplier", error))
                .toFuture();
    }

    /**
     * Create an order in the order service
     */
    @CircuitBreaker(name = "order-service", fallbackMethod = "createOrderFallback")
    @Retry(name = "order-service")
    @TimeLimiter(name = "order-service")
    public CompletableFuture<OrderInfo> createOrder(CreateOrderServiceRequest request) {
        log.info("📦 Creating order for customer: {} and product: {}", 
                request.getCustomerId(), request.getProductId());

        return orderServiceWebClient
                .post()
                .uri("/orders")
                .bodyValue(request)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new OrderServiceException("Failed to create order: " + errorBody))))
                .bodyToMono(OrderInfo.class)
                .doOnSuccess(order -> log.info("✅ Order created successfully: {}", order.getId()))
                .doOnError(error -> log.error("❌ Failed to create order", error))
                .toFuture();
    }

    /**
     * Get order details by ID
     */
    @CircuitBreaker(name = "order-service", fallbackMethod = "getOrderFallback")
    @Retry(name = "order-service")
    @TimeLimiter(name = "order-service")
    public CompletableFuture<OrderInfo> getOrder(Long orderId) {
        log.info("🔍 Fetching order details: {}", orderId);

        return orderServiceWebClient
                .get()
                .uri("/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                         response -> Mono.error(new OrderNotFoundException("Order not found: " + orderId)))
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new OrderServiceException("Failed to get order: " + errorBody))))
                .bodyToMono(OrderInfo.class)
                .doOnSuccess(order -> log.info("✅ Order details retrieved: {}", order.getId()))
                .doOnError(error -> log.error("❌ Failed to get order: {}", orderId, error))
                .toFuture();
    }

    /**
     * Get all suppliers for a product
     */
    @CircuitBreaker(name = "order-service", fallbackMethod = "getSuppliersForProductFallback")
    @Retry(name = "order-service")
    @TimeLimiter(name = "order-service")
    public CompletableFuture<List<SupplierInfo>> getSuppliersForProduct(String productId) {
        log.info("🏭 Fetching suppliers for product: {}", productId);

        return orderServiceWebClient
                .get()
                .uri("/suppliers/product/{productId}", productId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new OrderServiceException("Failed to get suppliers: " + errorBody))))
                .bodyToFlux(SupplierInfo.class)
                .collectList()
                .doOnSuccess(suppliers -> log.info("✅ Found {} suppliers for product: {}", suppliers.size(), productId))
                .doOnError(error -> log.error("❌ Failed to get suppliers for product: {}", productId, error))
                .toFuture();
    }

    /**
     * Update order status
     */
    @CircuitBreaker(name = "order-service", fallbackMethod = "updateOrderStatusFallback")
    @Retry(name = "order-service")
    @TimeLimiter(name = "order-service")
    public CompletableFuture<OrderInfo> updateOrderStatus(Long orderId, String status) {
        log.info("📝 Updating order {} status to: {}", orderId, status);

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(status);

        return orderServiceWebClient
                .put()
                .uri("/orders/{orderId}/status", orderId)
                .bodyValue(request)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new OrderServiceException("Failed to update order status: " + errorBody))))
                .bodyToMono(OrderInfo.class)
                .doOnSuccess(order -> log.info("✅ Order status updated: {} -> {}", orderId, status))
                .doOnError(error -> log.error("❌ Failed to update order status: {}", orderId, error))
                .toFuture();
    }

    // Fallback methods for circuit breaker

    public CompletableFuture<SupplierInfo> findClosestSupplierFallback(String productId, double latitude, double longitude, Exception ex) {
        log.warn("🔄 Using fallback for findClosestSupplier due to: {}", ex.getMessage());
        SupplierInfo fallbackSupplier = new SupplierInfo();
        fallbackSupplier.setName("Fallback Supplier");
        fallbackSupplier.setDistanceFromCustomer(999.0);
        return CompletableFuture.completedFuture(fallbackSupplier);
    }

    public CompletableFuture<OrderInfo> createOrderFallback(CreateOrderServiceRequest request, Exception ex) {
        log.warn("🔄 Using fallback for createOrder due to: {}", ex.getMessage());
        throw new OrderServiceException("Order service temporarily unavailable. Please try again later.");
    }

    public CompletableFuture<OrderInfo> getOrderFallback(Long orderId, Exception ex) {
        log.warn("🔄 Using fallback for getOrder due to: {}", ex.getMessage());
        throw new OrderServiceException("Order service temporarily unavailable. Cannot retrieve order: " + orderId);
    }

    public CompletableFuture<List<SupplierInfo>> getSuppliersForProductFallback(String productId, Exception ex) {
        log.warn("🔄 Using fallback for getSuppliersForProduct due to: {}", ex.getMessage());
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<OrderInfo> updateOrderStatusFallback(Long orderId, String status, Exception ex) {
        log.warn("🔄 Using fallback for updateOrderStatus due to: {}", ex.getMessage());
        throw new OrderServiceException("Order service temporarily unavailable. Cannot update order: " + orderId);
    }

    // Custom exceptions
    public static class OrderServiceException extends RuntimeException {
        public OrderServiceException(String message) {
            super(message);
        }
    }

    public static class SupplierNotFoundException extends RuntimeException {
        public SupplierNotFoundException(String message) {
            super(message);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }
}
