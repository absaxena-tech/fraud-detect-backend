package com.finguard.service;

import com.finguard.dto.*;
import com.finguard.entity.FraudAlert;
import com.finguard.repository.FraudAlertRepository;
import com.finguard.rules.FraudRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudRuleEngine ruleEngine;
    private final FraudAlertRepository alertRepository;
    private final KafkaTemplate<String, FraudAlertEvent> kafkaTemplate;

    @Qualifier("riskWebClient")
    private final WebClient riskWebClient;

    @Qualifier("ragWebClient")
    private final WebClient ragWebClient;

    @Value("${kafka.topics.fraud-alerts}")
    private String fraudAlertsTopic;

    @Value("${fraud.thresholds.score-flag}")
    private double scoreFlagThreshold;

    @Value("${fraud.thresholds.score-block}")
    private double scoreBlockThreshold;

    public void analyze(TransactionEvent tx) {
        log.info("Analyzing transaction {}", tx.getId());

        // Step 1: Rule engine evaluation
        FraudRuleEngine.RuleResult ruleResult = ruleEngine.evaluate(tx);
        double baseScore = ruleResult.score();

        log.info("Rule score for tx {}: {} rules={}", tx.getId(), baseScore, ruleResult.allRules());

        if (baseScore < scoreFlagThreshold) {
            log.info("Transaction {} below flag threshold ({}<{}). Skipping.", tx.getId(), baseScore, scoreFlagThreshold);
            return;
        }

        // Step 2: Risk enrichment
        RiskResponse risk = fetchRiskScore(tx.getAccountId());
        double finalScore = calculateFinalScore(baseScore, risk);

        // Step 3: Decide status
        String status = decideStatus(finalScore);

        // Step 4: Default explanation
        String explanation = "Fraud detected based on rules: " + ruleResult.allRules();

        // Step 5: Save alert
        FraudAlert alert = FraudAlert.builder()
                .transactionId(tx.getId())
                .fraudScore(finalScore)
                .ruleTriggered(ruleResult.primaryRule())
                .explanation(explanation)
                .status(status)
                .build();

        alert = alertRepository.save(alert);

        // Step 6: Publish Kafka event
        FraudAlertEvent event = FraudAlertEvent.builder()
                .transactionId(tx.getId())
                .accountId(tx.getAccountId())
                .amount(tx.getAmount())
                .fraudScore(finalScore)
                .ruleTriggered(ruleResult.primaryRule())
                .explanation(explanation)
                .status(status)
                .detectedAt(Instant.now())
                .build();

        kafkaTemplate.send(fraudAlertsTopic, tx.getAccountId(), event);

        log.info("Fraud alert published for tx {} status={} score={}", tx.getId(), status, finalScore);

        // Step 7: Async RAG explanation
        fetchRagExplanationAsync(tx, alert.getId());
    }

    private RiskResponse fetchRiskScore(String accountId) {
        try {
            return riskWebClient.get()
                    .uri("/api/risk/{accountId}", accountId)
                    .retrieve()
                    .bodyToMono(RiskResponse.class)
                    .timeout(Duration.ofSeconds(2))
                    .onErrorResume(ex -> {
                        log.warn("Risk service failed for account {}: {}", accountId, ex.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.warn("Risk service unavailable for account {}: {}", accountId, e.getMessage());
            return null;
        }
    }

    private double calculateFinalScore(double baseScore, RiskResponse risk) {
        if (risk == null) {
            return baseScore;
        }

        double riskScore = risk.getRiskScore();
        double enrichedScore = (baseScore * 0.6) + (riskScore * 0.4);

        log.info("Risk enrichment applied: baseScore={} riskScore={} finalScore={}",
                baseScore, riskScore, enrichedScore);

        return Math.min(1.0, enrichedScore);
    }

    private String decideStatus(double score) {
        return score >= scoreBlockThreshold ? "BLOCKED" : "FLAGGED";
    }

    private void fetchRagExplanationAsync(TransactionEvent tx, UUID alertId) {
        ragWebClient.post()
                .uri("/api/rag/explain")
                .bodyValue(tx)
                .retrieve()
                .bodyToMono(RagExplanationResponse.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> {
                    log.warn("RAG service failed for tx {}: {}", tx.getId(), ex.getMessage());
                    return Mono.empty();
                })
                .subscribe(ragResp -> {
                    if (ragResp != null && ragResp.getExplanation() != null) {
                        updateAlertExplanation(alertId, ragResp.getExplanation());
                    }
                });
    }

    private void updateAlertExplanation(UUID alertId, String explanation) {
        try {
            FraudAlert alert = alertRepository.findById(alertId).orElse(null);
            if (alert != null) {
                alert.setExplanation(explanation);
                alertRepository.save(alert);
                log.info("Updated explanation for alert {}", alertId);
            }
        } catch (Exception e) {
            log.warn("Failed to update explanation for alert {}: {}", alertId, e.getMessage());
        }
    }

    public List<FraudAlert> getOpenAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc("FLAGGED");
    }

    public List<FraudAlert> getAlertsByTransaction(UUID transactionId) {
        return alertRepository.findByTransactionId(transactionId);
    }
    public List<FraudAlert> getAlertsByUserId(String userId) {
        return alertRepository.findByUserId(userId);
    }
}

