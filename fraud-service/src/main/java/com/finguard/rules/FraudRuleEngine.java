package com.finguard.rules;

import com.finguard.dto.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based fraud scoring engine.
 * Each rule contributes a partial score; they are summed and capped at 1.0.
 */
@Slf4j
@Component
public class FraudRuleEngine {

    private final StringRedisTemplate redis;

    @Value("${fraud.thresholds.high-amount}")
    private BigDecimal highAmount;

    @Value("${fraud.thresholds.velocity-window-seconds}")
    private long velocityWindowSeconds;

    @Value("${fraud.thresholds.velocity-max-count}")
    private int velocityMaxCount;

    public FraudRuleEngine(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RuleResult evaluate(TransactionEvent tx) {
        List<String> triggeredRules = new ArrayList<>();
        double totalScore = 0.0;

        // Rule 1: High amount
        if (tx.getAmount() != null && tx.getAmount().compareTo(highAmount) > 0) {
            double contribution = Math.min(0.4, tx.getAmount()
                    .divide(highAmount, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 0.1);
            totalScore += contribution;
            triggeredRules.add("HIGH_AMOUNT");
            log.debug("Rule HIGH_AMOUNT fired for tx {} (+{})", tx.getId(), contribution);
        }

        // Rule 2: Velocity – too many transactions in the window
        String velocityKey = "velocity:" + tx.getAccountId();
        Long count = redis.opsForValue().increment(velocityKey);
        if (count != null && count == 1) {
            redis.expire(velocityKey, Duration.ofSeconds(velocityWindowSeconds));
        }
        if (count != null && count > velocityMaxCount) {
            double contribution = Math.min(0.5, (count - velocityMaxCount) * 0.05);
            totalScore += contribution;
            triggeredRules.add("VELOCITY_EXCEEDED");
            log.debug("Rule VELOCITY_EXCEEDED fired for account {} count={} (+{})", tx.getAccountId(), count, contribution);
        }

        // Rule 3: Suspicious merchant categories
        if (tx.getMerchantCategory() != null) {
            String cat = tx.getMerchantCategory().toUpperCase();
            if (cat.contains("CRYPTO") || cat.contains("GAMBLING") || cat.contains("MONEY_TRANSFER")) {
                totalScore += 0.3;
                triggeredRules.add("SUSPICIOUS_CATEGORY");
            }
        }

        // Rule 4: Missing device / IP (anomalous context)
        if (tx.getDeviceId() == null || tx.getIpAddress() == null) {
            totalScore += 0.15;
            triggeredRules.add("MISSING_CONTEXT");
        }

        double finalScore = Math.min(1.0, totalScore);
        String primaryRule = triggeredRules.isEmpty() ? "NONE" : triggeredRules.get(0);
        return new RuleResult(finalScore, primaryRule, triggeredRules);
    }

    public record RuleResult(double score, String primaryRule, List<String> allRules) {}
}
