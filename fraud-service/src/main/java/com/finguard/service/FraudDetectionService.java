package com.finguard.service;

import com.finguard.dto.*;
import com.finguard.entity.FraudAlert;
import com.finguard.repository.FraudAlertRepository;
import com.finguard.rules.FraudRuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FraudDetectionService {

    private final FraudRuleEngine ruleEngine;
    private final FraudAlertRepository alertRepository;
    private final KafkaTemplate<String, FraudAlertEvent> kafkaTemplate;
    private final WebClient riskWebClient;
    private final WebClient ragWebClient;

    @Value("${kafka.topics.fraud-alerts}")
    private String fraudAlertsTopic;

    @Value("${fraud.thresholds.score-flag}")
    private double scoreFlagThreshold;

    @Value("${fraud.thresholds.score-block}")
    private double scoreBlockThreshold;

    public FraudDetectionService(
            FraudRuleEngine ruleEngine,
            FraudAlertRepository alertRepository,
            KafkaTemplate<String, FraudAlertEvent> kafkaTemplate,
            @Qualifier("riskWebClient") WebClient riskWebClient,
            @Qualifier("ragWebClient") WebClient ragWebClient) {
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.riskWebClient = riskWebClient;
        this.ragWebClient = ragWebClient;
    }

    public void analyze(TransactionEvent tx) {
        log.info("Analyzing transaction {}", tx.getId());

        // Step 1: Rule engine score
        FraudRuleEngine.RuleResult ruleResult = ruleEngine.evaluate(tx);
        double baseScore = ruleResult.score();
        log.info("Rule score for tx {}: {} rules={}", tx.getId(), baseScore, ruleResult.allRules());

        if (baseScore < scoreFlagThreshold) {
            log.info("Transaction {} below flag threshold ({}<{}), skipping.", tx.getId(), baseScore, scoreFlagThreshold);
            return;
        }

        // Step 2: Enrich with Risk service score (synchronous REST via WebClient)
        double enrichedScore = baseScore;
        String riskLevel = "MEDIUM";
        try {
            RiskResponse risk = riskWebClient.get()
                    .uri("/api/risk/{accountId}", tx.getAccountId())
                    .retrieve()
                    .bodyToMono(RiskResponse.class)
                    .block();
            if (risk != null) {
                enrichedScore = Math.min(1.0, baseScore * 0.6 + risk.getRiskScore() * 0.4);
                riskLevel = risk.getRiskLevel();
                log.info("Risk enrichment for {}: riskScore={} level={}", tx.getAccountId(), risk.getRiskScore(), riskLevel);
            }
        } catch (Exception e) {
            log.warn("Risk service unavailable, using base score: {}", e.getMessage());
        }

        // Step 3: Fetch RAG explanation (on-demand REST)
        String explanation = "Fraud detected based on rules: " + ruleResult.allRules();
        try {
            RagExplanationResponse ragResp = ragWebClient.post()
                    .uri("/api/rag/explain")
                    .bodyValue(tx)
                    .retrieve()
                    .bodyToMono(RagExplanationResponse.class)
                    .block();
            if (ragResp != null && ragResp.getExplanation() != null) {
                explanation = ragResp.getExplanation();
            }
        } catch (Exception e) {
            log.warn("RAG service unavailable, using default explanation: {}", e.getMessage());
        }

        // Step 4: Persist alert
        String status = enrichedScore >= scoreBlockThreshold ? "BLOCKED" : "FLAGGED";
        FraudAlert alert = FraudAlert.builder()
                .transactionId(tx.getId())
                .fraudScore(enrichedScore)
                .ruleTriggered(ruleResult.primaryRule())
                .explanation(explanation)
                .status(status)
                .build();
        alertRepository.save(alert);

        // Step 5: Publish to Kafka fraud-alerts topic (async)
        FraudAlertEvent event = FraudAlertEvent.builder()
                .transactionId(tx.getId())
                .accountId(tx.getAccountId())
                .amount(tx.getAmount())
                .fraudScore(enrichedScore)
                .ruleTriggered(ruleResult.primaryRule())
                .explanation(explanation)
                .status(status)
                .detectedAt(Instant.now())
                .build();

        kafkaTemplate.send(fraudAlertsTopic, tx.getAccountId(), event);
        log.info("Fraud alert published for tx {} status={} score={}", tx.getId(), status, enrichedScore);
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
