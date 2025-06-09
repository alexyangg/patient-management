package com.pm.apigateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/*
Filter class, allows us to intercept HTTP requests, apply custom logic, and decide whether to
continue processing the request or cancel it.
 */
@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;

    // initialize a web client using the base url passed in as an environment variable
    public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder,
                                             @Value("${auth.service.url}") String authServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    // Spring Cloud Gateway will automatically apply the filter to all requests
    @Override
    public GatewayFilter apply(Object config) {
        // exchange: Java object that represents the current HTTP request/response
        // chain: chain of filters the current request still has to go through
        return (exchange, chain) -> {
            // get authorization header from request
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (token == null || !token.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return webClient.get() // start building an HTTP GET request using the WebClient
                    .uri("/validate") // set URI path for the request to "/validate"
                    .header(HttpHeaders.AUTHORIZATION, token) // add "Authorization" header with provided token
                    .retrieve() // send request and prepare to retrieve the response
                    .toBodilessEntity() // extract response without a body (only headers/status are kept)
                    .then(chain.filter(exchange)); // once response received, continue filter chain for original request
        };
    }
}
