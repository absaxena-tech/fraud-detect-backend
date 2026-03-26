package com.finguard.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessMs;
    private final long refreshMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessMs,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshMs) {
        this.key       = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessMs  = accessMs;
        this.refreshMs = refreshMs;
    }

    /** Short-lived access token (15 min). Contains userId, email, role, type=access */
    public String generateAccessToken(UUID userId, String email, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role",  role)
                .claim("type",  "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessMs))
                .signWith(key)
                .compact();
    }

    /** Long-lived refresh token (7 days). Contains only userId, type=refresh */
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshMs))
                .signWith(key)
                .compact();
    }

    /** Parse claims — throws JwtException on any failure */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                   .parseSignedClaims(token).getPayload();
    }

    public UUID    extractUserId(String token) { return UUID.fromString(parse(token).getSubject()); }
    public boolean isRefreshToken(String token) { return "refresh".equals(parse(token).get("type")); }
    public boolean isAccessToken(String token)  { return "access".equals(parse(token).get("type")); }

    public boolean isValid(String token) {
        try { parse(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public long getAccessMs()  { return accessMs; }
    public long getRefreshMs() { return refreshMs; }
}
