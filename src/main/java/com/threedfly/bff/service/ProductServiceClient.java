package com.threedfly.bff.service;

import com.threedfly.bff.dto.external.product.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with Product Service
 * 
 * Handles:
 * - Creating payments
 * - Executing payments
 * - Payment status management
 * 
 * Includes circuit breaker, retry, and timeout patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

    @Qualifier("productServiceWebClient")
    private final WebClient productServiceWebClient;

    /**
     * Create a payment in the product service
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "createPaymentFallback")
    @Retry(name = "product-service")
    @TimeLimiter(name = "product-service")
    public CompletableFuture<PaymentInfo> createPayment(CreatePaymentRequest request) {
        log.info("💳 Creating payment for order: {} with amount: {}", 
                request.getOrderId(), request.getTotalAmount());

        return productServiceWebClient
                .post()
                .uri("/payments")
                .bodyValue(request)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new ProductServiceException("Failed to create payment: " + errorBody))))
                .bodyToMono(PaymentInfo.class)
                .doOnSuccess(payment -> log.info("✅ Payment created successfully: {}", payment.getId()))
                .doOnError(error -> log.error("❌ Failed to create payment", error))
                .toFuture();
    }

    /**
     * Execute a payment in the product service
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "executePaymentFallback")
    @Retry(name = "product-service")
    @TimeLimiter(name = "product-service")
    public CompletableFuture<PaymentInfo> executePayment(String paymentId, ExecutePaymentRequest request) {
        log.info("✅ Executing payment: {}", paymentId);

        return productServiceWebClient
                .post()
                .uri("/payments/{paymentId}/execute", paymentId)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                         response -> Mono.error(new PaymentNotFoundException("Payment not found: " + paymentId)))
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new ProductServiceException("Failed to execute payment: " + errorBody))))
                .bodyToMono(PaymentInfo.class)
                .doOnSuccess(payment -> log.info("✅ Payment executed successfully: {}", payment.getId()))
                .doOnError(error -> log.error("❌ Failed to execute payment: {}", paymentId, error))
                .toFuture();
    }

    /**
     * Get payment details by ID
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "getPaymentFallback")
    @Retry(name = "product-service")
    @TimeLimiter(name = "product-service")
    public CompletableFuture<PaymentInfo> getPayment(Long paymentId) {
        log.info("🔍 Fetching payment details: {}", paymentId);

        return productServiceWebClient
                .get()
                .uri("/payments/{paymentId}", paymentId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                         response -> Mono.error(new PaymentNotFoundException("Payment not found: " + paymentId)))
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new ProductServiceException("Failed to get payment: " + errorBody))))
                .bodyToMono(PaymentInfo.class)
                .doOnSuccess(payment -> log.info("✅ Payment details retrieved: {}", payment.getId()))
                .doOnError(error -> log.error("❌ Failed to get payment: {}", paymentId, error))
                .toFuture();
    }

    /**
     * Get payments for an order
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "getPaymentsByOrderFallback")
    @Retry(name = "product-service")
    @TimeLimiter(name = "product-service")
    public CompletableFuture<java.util.List<PaymentInfo>> getPaymentsByOrder(Long orderId) {
        log.info("🔍 Fetching payments for order: {}", orderId);

        return productServiceWebClient
                .get()
                .uri("/payments/order/{orderId}", orderId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new ProductServiceException("Failed to get payments for order: " + errorBody))))
                .bodyToFlux(PaymentInfo.class)
                .collectList()
                .doOnSuccess(payments -> log.info("✅ Found {} payments for order: {}", payments.size(), orderId))
                .doOnError(error -> log.error("❌ Failed to get payments for order: {}", orderId, error))
                .toFuture();
    }

    /**
     * Get product information
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "getProductFallback")
    @Retry(name = "product-service")
    @TimeLimiter(name = "product-service")
    public CompletableFuture<ProductInfo> getProduct(String productId) {
        log.info("🔍 Fetching product details: {}", productId);

        return productServiceWebClient
                .get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                         response -> Mono.error(new ProductNotFoundException("Product not found: " + productId)))
                .onStatus(httpStatus -> httpStatus.isError(),
                         response -> response.bodyToMono(String.class)
                                 .flatMap(errorBody -> Mono.error(new ProductServiceException("Failed to get product: " + errorBody))))
                .bodyToMono(ProductInfo.class)
                .doOnSuccess(product -> log.info("✅ Product details retrieved: {}", product.getId()))
                .doOnError(error -> log.error("❌ Failed to get product: {}", productId, error))
                .toFuture();
    }

    // Fallback methods for circuit breaker

    public CompletableFuture<PaymentInfo> createPaymentFallback(CreatePaymentRequest request, Exception ex) {
        log.warn("🔄 Using fallback for createPayment due to: {}", ex.getMessage());
        throw new ProductServiceException("Payment service temporarily unavailable. Please try again later.");
    }

    public CompletableFuture<PaymentInfo> executePaymentFallback(String paymentId, ExecutePaymentRequest request, Exception ex) {
        log.warn("🔄 Using fallback for executePayment due to: {}", ex.getMessage());
        throw new ProductServiceException("Payment service temporarily unavailable. Cannot execute payment: " + paymentId);
    }

    public CompletableFuture<PaymentInfo> getPaymentFallback(Long paymentId, Exception ex) {
        log.warn("🔄 Using fallback for getPayment due to: {}", ex.getMessage());
        throw new ProductServiceException("Payment service temporarily unavailable. Cannot retrieve payment: " + paymentId);
    }

    public CompletableFuture<java.util.List<PaymentInfo>> getPaymentsByOrderFallback(Long orderId, Exception ex) {
        log.warn("🔄 Using fallback for getPaymentsByOrder due to: {}", ex.getMessage());
        return CompletableFuture.completedFuture(java.util.List.of());
    }

    public CompletableFuture<ProductInfo> getProductFallback(String productId, Exception ex) {
        log.warn("🔄 Using fallback for getProduct due to: {}", ex.getMessage());
        ProductInfo fallbackProduct = new ProductInfo();
        fallbackProduct.setId(productId);
        fallbackProduct.setName("Product Temporarily Unavailable");
        fallbackProduct.setPrice(java.math.BigDecimal.ZERO);
        return CompletableFuture.completedFuture(fallbackProduct);
    }

    // Custom exceptions
    public static class ProductServiceException extends RuntimeException {
        public ProductServiceException(String message) {
            super(message);
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
}
