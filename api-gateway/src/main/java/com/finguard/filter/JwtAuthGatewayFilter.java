package com.finguard.filter;

import com.finguard.config.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway filter applied to every protected route.
 *
 * What it does:
 *   1. Reads the Bearer token from the Authorization header
 *   2. Validates the JWT (signature + expiry + type=access)
 *   3. Extracts userId, role, email from claims
 *   4. Removes Authorization header from the forwarded request
 *      (downstream services NEVER see the raw JWT)
 *   5. Injects X-User-Id, X-User-Role, X-User-Email into the forwarded request
 *
 * Downstream services trust these plain headers — no JWT library needed there.
 */
@Slf4j
@Component
public class JwtAuthGatewayFilter extends AbstractGatewayFilterFactory<JwtAuthGatewayFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthGatewayFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Extract Bearer token
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return reject(exchange.getResponse(), "Authorization header missing or malformed");
            }

            String token = authHeader.substring(7);

            // 2. Validate JWT — single point of truth for the whole system
            Claims claims = jwtUtil.validateAndExtract(token);
            if (claims == null) {
                return reject(exchange.getResponse(), "Token is invalid or has expired");
            }

            String userId = claims.getSubject();
            String role   = claims.get("role",  String.class);
            String email  = claims.get("email", String.class);

            log.debug("Gateway auth ok — userId={} role={} path={}",
                    userId, role, request.getURI().getPath());

            // 3. Mutate request:
            //    - Strip Authorization header (downstream never gets the JWT)
            //    - Add X-User-Id, X-User-Role, X-User-Email
            ServerHttpRequest mutated = request.mutate()
                    .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                    .header("X-User-Id",    userId)
                    .header("X-User-Role",  role   != null ? role  : "USER")
                    .header("X-User-Email", email  != null ? email : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        };
    }

    private Mono<Void> reject(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"status\":401,\"error\":\"%s\"}", message);
        DataBuffer buf = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buf));
    }

    public static class Config {
        // No per-route config needed
    }
}
