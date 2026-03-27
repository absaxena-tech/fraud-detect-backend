package com.finguard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FraudAlertEvent {
    private UUID        transactionId;
    private String      accountId;    // userId as string
    private String      userEmail;    // user's email — send alert here
    private BigDecimal  amount;
    private double      fraudScore;
    private String      ruleTriggered;
    private String      explanation;
    private String      status;       // FLAGGED | BLOCKED
    private Instant     detectedAt;
}
