package com.finguard.service;

import com.finguard.dto.*;
import com.finguard.entity.User;
import com.finguard.repository.UserRepository;
import com.finguard.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        repo;
    private final PasswordEncoder       encoder;
    private final JwtUtil               jwt;
    private final AuthenticationManager authManager;
    private final StringRedisTemplate   redis;

    private static final String REFRESH_KEY = "refresh:";

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (repo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());

        User user = User.builder()
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .build();

        user = repo.save(user);
        log.info("Registered user {} id={}", user.getEmail(), user.getId());
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = repo.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        log.info("Login ok user={}", user.getId());
        return buildResponse(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        String token = req.getRefreshToken();

        // Validate token structure and type
        if (!jwt.isValid(token) || !jwt.isRefreshToken(token))
            throw new BadCredentialsException("Invalid refresh token");

        UUID userId = jwt.extractUserId(token);
        String stored = redis.opsForValue().get(REFRESH_KEY + userId);

        // Must match what we stored — prevents reuse after rotation
        if (!token.equals(stored))
            throw new BadCredentialsException("Refresh token revoked or already used");

        User user = repo.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        log.info("Token refreshed for userId={}", userId);
        return buildResponse(user);
    }

    public void logout(UUID userId) {
        redis.delete(REFRESH_KEY + userId);
        log.info("Logged out userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────

    private AuthResponse buildResponse(User user) {
        String access  = jwt.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refresh = jwt.generateRefreshToken(user.getId());

        // Store refresh token; replaces previous — automatic rotation
        redis.opsForValue().set(
            REFRESH_KEY + user.getId(),
            refresh,
            Duration.ofMillis(jwt.getRefreshMs())
        );

        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresIn(jwt.getAccessMs() / 1000)
                .user(UserInfo.from(user))
                .build();
    }
}
