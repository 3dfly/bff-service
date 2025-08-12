package com.threedfly.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for external microservices
 */
@Configuration
@ConfigurationProperties(prefix = "services")
@Data
public class ServicesConfig {

    private OrderService orderService = new OrderService();
    private ProductService productService = new ProductService();
    private String apiKey;
    
    // Convenience methods for URL access
    public String getOrderServiceUrl() {
        return orderService.getBaseUrl();
    }
    
    public String getProductServiceUrl() {
        return productService.getBaseUrl();
    }

    @Data
    public static class OrderService {
        private String baseUrl = "http://localhost:8080";
        private Duration timeout = Duration.ofSeconds(10);
        private Retry retry = new Retry();
    }

    @Data
    public static class ProductService {
        private String baseUrl = "http://localhost:8081";
        private Duration timeout = Duration.ofSeconds(10);
        private Retry retry = new Retry();
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofSeconds(1);
        private boolean enableExponentialBackoff = true;
        private double exponentialBackoffMultiplier = 2.0;
    }
}
