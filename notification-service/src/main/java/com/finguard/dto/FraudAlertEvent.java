package com.finguard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {
    private UUID transactionId;
    private String accountId;
    private BigDecimal amount;
    private double fraudScore;
    private String ruleTriggered;
    private String explanation;
    private String status;     // FLAGGED | BLOCKED
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant detectedAt;
}
