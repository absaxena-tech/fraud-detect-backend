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
public class FraudDetectionService {

    private final FraudRuleEngine ruleEngine;
    private final FraudAlertRepository alertRepository;
    private final KafkaTemplate<String, FraudAlertEvent> kafkaTemplate;
    private final WebClient riskWebClient;
    private final WebClient ragWebClient;
    private final WebClient transactionWebClient;

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
            @Qualifier("ragWebClient") WebClient ragWebClient,
            @Qualifier("transactionWebClient") WebClient transactionWebClient) {
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.riskWebClient = riskWebClient;
        this.ragWebClient = ragWebClient;
        this.transactionWebClient = transactionWebClient;
    }

    /**
     * Main entry point to analyze a transaction
     */
    public void analyze(TransactionEvent tx) {
        log.info("Analyzing transaction {} for account {}", tx.getId(), tx.getAccountId());

        // Step 1: Rule engine
        FraudRuleEngine.RuleResult ruleResult = ruleEngine.evaluate(tx);
        double baseScore = ruleResult.score();
        log.info("Rule score tx={}: {} rules={}", tx.getId(), baseScore, ruleResult.allRules());

        // Step 2: Always update risk profile
        updateRiskProfile(tx);

        // Step 3: If below flag threshold → mark SUCCESS and exit
        if (baseScore < scoreFlagThreshold) {
            log.info("Transaction {} is clean (score={} < {}), marking SUCCESS",
                    tx.getId(), baseScore, scoreFlagThreshold);
            updateTransactionStatus(tx.getId(), "SUCCESS");
            return;
        }

        // Step 4: Enrich with risk service score
        double enrichedScore = baseScore;
        try {
            RiskResponse risk = riskWebClient.get()
                    .uri("/api/risk/{accountId}", tx.getAccountId())
                    .retrieve()
                    .bodyToMono(RiskResponse.class)
                    .block();
            if (risk != null) {
                enrichedScore = Math.min(1.0, baseScore * 0.6 + risk.getRiskScore() * 0.4);
                log.info("Risk enrichment account={}: riskScore={} level={}",
                        tx.getAccountId(), risk.getRiskScore(), risk.getRiskLevel());
            }
        } catch (Exception e) {
            log.warn("Risk service unavailable for tx {}, using base score: {}", tx.getId(), e.getMessage());
        }

        // Step 5: Determine status
        String status = enrichedScore >= scoreBlockThreshold ? "BLOCKED" : "FLAGGED";

        // Step 6: Create explanation
        String explanation = "Fraud detected based on rules: " + ruleResult.allRules();
        try {
            RagExplanationResponse rag = ragWebClient.post()
                    .uri("/api/rag/explain")
                    .bodyValue(tx)
                    .retrieve()
                    .bodyToMono(RagExplanationResponse.class)
                    .block();
            if (rag != null && rag.getExplanation() != null) {
                explanation = rag.getExplanation();
            }
        } catch (Exception e) {
            log.warn("RAG service unavailable for tx {}, using default explanation: {}", tx.getId(), e.getMessage());
        }

        // Step 7: Check for duplicate alerts (idempotency)
        if (alertRepository.existsByTransactionId(tx.getId())) {
            log.warn("Alert already exists for transaction {}. Skipping save.", tx.getId());
        } else {
            // Step 8: Persist fraud alert
            FraudAlert alert = FraudAlert.builder()
                    .transactionId(tx.getId())
                    .fraudScore(enrichedScore)
                    .ruleTriggered(ruleResult.primaryRule())
                    .explanation(explanation)
                    .status(status)
                    .build();
            alertRepository.save(alert);

            // Step 9: Update transaction status
            updateTransactionStatus(tx.getId(), status);

            // Step 10: Publish fraud alert to Kafka
            FraudAlertEvent event = FraudAlertEvent.builder()
                    .transactionId(tx.getId())
                    .accountId(tx.getAccountId())
                    .userEmail(tx.getUserEmail())
                    .amount(tx.getAmount())
                    .fraudScore(enrichedScore)
                    .ruleTriggered(ruleResult.primaryRule())
                    .explanation(explanation)
                    .status(status)
                    .detectedAt(Instant.now())
                    .build();

            kafkaTemplate.send(fraudAlertsTopic, tx.getAccountId(), event);
            log.warn("Fraud alert published tx={} status={} score={}", tx.getId(), status, enrichedScore);
        }
    }

    /**
     * Always call risk-service to update the account's profile
     */
    private void updateRiskProfile(TransactionEvent tx) {
        try {
            riskWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/risk/{accountId}/update")
                            .queryParam("amount", tx.getAmount())
                            .build(tx.getAccountId()))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.debug("Risk profile updated for account={} amount={}", tx.getAccountId(), tx.getAmount());
        } catch (Exception e) {
            log.warn("Could not update risk profile for account={}: {}", tx.getAccountId(), e.getMessage());
        }
    }

    /**
     * Call transaction-service to update the transaction status
     */
    private void updateTransactionStatus(UUID transactionId, String status) {
        try {
            transactionWebClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transactions/{id}/status")
                            .queryParam("status", status)
                            .build(transactionId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Transaction {} status set to {}", transactionId, status);
        } catch (Exception e) {
            log.warn("Could not update status for transaction {}: {}", transactionId, e.getMessage());
        }
    }

    // Query methods
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
