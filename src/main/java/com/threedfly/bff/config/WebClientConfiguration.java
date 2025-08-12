package com.threedfly.bff.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Creates dedicated WebClient beans for each service.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebClientConfiguration {

    private final ServicesConfig servicesConfig;

    @Bean
    public WebClient orderServiceWebClient() {
        return createWebClient(
            servicesConfig.getOrderServiceUrl(), 
            "order-service-api-key",
            "Order Service"
        );
    }

    @Bean
    public WebClient productServiceWebClient() {
        return createWebClient(
            servicesConfig.getProductServiceUrl(), 
            "product-service-api-key",
            "Product Service"
        );
    }

    private WebClient createWebClient(String baseUrl, String apiKey, String serviceName) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest(serviceName))
                .filter(logResponse(serviceName))
                .filter(addApiKeyFilter(apiKey))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private ExchangeFilterFunction logRequest(String serviceName) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("🔄 Outgoing Request to {}: {} {}", serviceName, clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse(String serviceName) {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("📨 Incoming Response from {}: {} {}", serviceName, clientResponse.statusCode(), clientResponse.request().getURI());
            if (clientResponse.statusCode().isError()) {
                log.warn("⚠️ Error Response from {}: {} {}", serviceName, clientResponse.statusCode(), clientResponse.request().getURI());
            }
            return Mono.just(clientResponse);
        });
    }

    private ExchangeFilterFunction addApiKeyFilter(String apiKey) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            return Mono.just(org.springframework.web.reactive.function.client.ClientRequest.from(clientRequest)
                    .header("X-API-Key", apiKey)
                    .build());
        });
    }
}
