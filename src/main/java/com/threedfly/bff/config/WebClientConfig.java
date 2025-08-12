package com.threedfly.bff.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient Configuration for Microservices Communication
 * 
 * Configures WebClient instances for communicating with:
 * - Order Service
 * - Product Service
 * 
 * Includes timeout configuration, logging, and error handling.
 */
@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${services.order-service.base-url}")
    private String orderServiceBaseUrl;

    @Value("${services.product-service.base-url}")
    private String productServiceBaseUrl;

    @Value("${services.api-key}")
    private String apiKey;

    @Value("${webclient.connect-timeout:5s}")
    private Duration connectTimeout;

    @Value("${webclient.read-timeout:30s}")
    private Duration readTimeout;

    @Value("${webclient.write-timeout:30s}")
    private Duration writeTimeout;

    /**
     * Base WebClient with common configuration
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS));
                });

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequestFilter())
                .filter(logResponseFilter())
                .filter(addApiKeyFilter());
    }

    /**
     * WebClient for Order Service communication
     */
    @Bean
    public WebClient orderServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(orderServiceBaseUrl)
                .build();
    }

    /**
     * WebClient for Product Service communication
     */
    @Bean
    public WebClient productServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(productServiceBaseUrl)
                .build();
    }

    /**
     * Request logging filter
     */
    private ExchangeFilterFunction logRequestFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("🔄 Outgoing Request: {} {}", clientRequest.method(), clientRequest.url());
            log.debug("📝 Request Headers: {}", clientRequest.headers());
            return Mono.just(clientRequest);
        });
    }

    /**
     * Response logging filter
     */
    private ExchangeFilterFunction logResponseFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("📨 Incoming Response: {} {}", clientResponse.statusCode(), clientResponse.request().getURI());
            if (clientResponse.statusCode().isError()) {
                log.warn("⚠️ Error Response: {}", clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * Add API key to all requests
     */
    private ExchangeFilterFunction addApiKeyFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            return Mono.just(
                    org.springframework.web.reactive.function.client.ClientRequest.from(clientRequest)
                            .header("X-API-Key", apiKey)
                            .header("X-Service-Name", "bff-service")
                            .build()
            );
        });
    }
}
