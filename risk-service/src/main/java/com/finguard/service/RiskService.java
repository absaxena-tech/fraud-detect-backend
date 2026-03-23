package com.finguard.service;

import com.finguard.dto.RiskResponse;
import com.finguard.entity.RiskProfile;
import com.finguard.repository.RiskProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;


@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final String CACHE_PREFIX = "risk:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RiskProfileRepository profileRepository;
    private final StringRedisTemplate redis;

    public RiskResponse getRisk(String accountId) {
        // Check Redis cache first
        String cacheKey = CACHE_PREFIX + accountId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for account {}", accountId);
            return parseCache(accountId, cached);
        }

        // Load or create profile
        RiskProfile profile = profileRepository.findById(accountId)
                .orElse(RiskProfile.builder()
                        .accountId(accountId)
                        .riskScore(0.1)
                        .transactionCount(0)
                        .avgAmount(BigDecimal.ZERO)
                        .build());

        double score = computeScore(profile);
        String level = toLevel(score);

        // Cache for next call
        redis.opsForValue().set(cacheKey, score + ":" + level, CACHE_TTL);

        return RiskResponse.builder()
                .accountId(accountId)
                .riskScore(score)
                .riskLevel(level)
                .reason("Based on " + profile.getTransactionCount() + " historical transactions")
                .build();
    }

    public void updateProfile(String accountId, BigDecimal transactionAmount) {
        RiskProfile profile = profileRepository.findById(accountId)
                .orElse(RiskProfile.builder()
                        .accountId(accountId)
                        .riskScore(0.1)
                        .transactionCount(0)
                        .avgAmount(BigDecimal.ZERO)
                        .build());

        int newCount = profile.getTransactionCount() + 1;
        BigDecimal newAvg = profile.getAvgAmount()
                .multiply(BigDecimal.valueOf(profile.getTransactionCount()))
                .add(transactionAmount)
                .divide(BigDecimal.valueOf(newCount), 4, java.math.RoundingMode.HALF_UP);

        profile.setTransactionCount(newCount);
        profile.setAvgAmount(newAvg);
        profile.setRiskScore(computeScore(profile));
        profileRepository.save(profile);

        // Invalidate cache
        redis.delete(CACHE_PREFIX + accountId);
        log.info("Risk profile updated for account {}: count={} avgAmount={}", accountId, newCount, newAvg);
    }

    private double computeScore(RiskProfile profile) {
        double score = 0.1; // baseline

        // Factor 1: transaction count (more history = lower risk)
        if (profile.getTransactionCount() > 100) score -= 0.05;
        else if (profile.getTransactionCount() < 5) score += 0.2;

        // Factor 2: average transaction amount
        if (profile.getAvgAmount() != null) {
            double avg = profile.getAvgAmount().doubleValue();
            if (avg > 5000) score += 0.25;
            else if (avg > 1000) score += 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private String toLevel(double score) {
        if (score >= 0.8) return "CRITICAL";
        if (score >= 0.6) return "HIGH";
        if (score >= 0.3) return "MEDIUM";
        return "LOW";
    }

    private RiskResponse parseCache(String accountId, String cached) {
        String[] parts = cached.split(":");
        double score = parts.length > 0 ? Double.parseDouble(parts[0]) : 0.1;
        String level = parts.length > 1 ? parts[1] : "LOW";
        return RiskResponse.builder()
                .accountId(accountId)
                .riskScore(score)
                .riskLevel(level)
                .reason("Cached result")
                .build();
    }
}
