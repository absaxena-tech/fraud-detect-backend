package com.finguard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RiskResponse {
    private String accountId;
    private double riskScore;
    private String riskLevel;   // LOW | MEDIUM | HIGH | CRITICAL
    private String reason;
}
