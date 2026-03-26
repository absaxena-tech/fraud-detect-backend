package com.finguard.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Validate the token and return its claims.
     * Returns null if the token is expired, malformed, or has wrong signature.
     * Only accepts tokens with type=access (refresh tokens are rejected).
     */
    public Claims validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type", String.class))) {
                log.debug("Rejected token with type={}", claims.get("type"));
                return null;
            }
            return claims;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token invalid: {}", e.getMessage());
            return null;
        }
    }
}
