package com.pm.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice // tells Spring that this class handles an exception
public class JwtValidationException {

    // Mono object used by filter chain to tell Spring the current filter is finished
    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    public Mono<Void> handleUnauthorizedException(ServerWebExchange exchange) {
        // custom exception, when API gateway tries to call validation endpoint on auth server
        // and auth server returns 401 unauthorized, instead of API gateway returning status 500,
        // intercept that response and ensure that we send back a status 401
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
