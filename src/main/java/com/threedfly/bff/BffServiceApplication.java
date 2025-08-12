package com.threedfly.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * BFF (Backend for Frontend) Service Application
 * 
 * This service acts as a facade for the microservices architecture,
 * orchestrating communication between order-service, product-service,
 * and other downstream services.
 * 
 * Main responsibilities:
 * - Order orchestration and workflow management
 * - Service communication and integration
 * - Request/response transformation
 * - Circuit breaking and resilience
 * - API aggregation and simplification for frontend clients
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BffServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffServiceApplication.class, args);
	}
}
