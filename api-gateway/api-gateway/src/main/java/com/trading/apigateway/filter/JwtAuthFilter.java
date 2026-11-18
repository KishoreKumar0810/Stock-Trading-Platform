package com.trading.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;

/**
 * JWT Authentication Filter — API Gateway.
 *
 * Validates JWT on every protected request.
 * Extracts userId from token claims.
 * Adds X-User-Id header → downstream services read this.
 *
 * Public endpoints (/register, /login) → bypassed.
 * All other endpoints → JWT required.
 *
 * User Service GENERATES tokens on login.
 * API Gateway VALIDATES tokens on every request.
 * Clean separation of responsibilities.
 */

@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    public JwtAuthFilter(){
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().toString();

            // skip jwt for public endpoints
            if(isPublicEndpoint(path)) {
                log.debug("public endpoint is {}", path);
                return chain.filter(exchange);
            }

            // check authorization header
            if(!request.getHeaders().containsKey("Authorization")) {
                log.warn("Authorization header is missing {}", path);
                return unauthorizedResponse(exchange, "Missing Authorization Header");
            }

            String authHeader = request.getHeaders().getFirst("Authorization");

            if(authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorizedResponse(exchange, "Invalid Authorization format");
            }

            String token = authHeader.substring(7);
            try {
                // Validate JWT and extract claims
                Claims claims = extractcClaims(token);
                String userId = claims.get("userId").toString();
                String email = claims.getSubject();

                log.debug("JWT valid - userId: {} email: {}", userId, email);

                // Add userId to request headers
                // Downstream services read X-User-Id
                ServerHttpRequest modifiedRequest = request
                        .mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Email", email)
                        .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } catch (Exception e) {
                log.warn("Invalid JWT Token: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid or Expired token");
            }
        };
    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/register") ||
                path.contains("/login") ||
                path.contains("/health") ||
                path.startsWith("/ws");
    }

    private Claims extractcClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        log.warn("Unauthorized: {}", message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /**
     * Empty Config class — required by
     * AbstractGatewayFilterFactory pattern.
     * No per-route configuration needed.
     * Same JWT validation for all protected routes.
     */
    public static class Config{}
}
