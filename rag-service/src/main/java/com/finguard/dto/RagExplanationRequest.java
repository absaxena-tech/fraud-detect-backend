package com.finguard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagExplanationRequest {
    private TransactionEvent transaction;
    private String ruleExplanation;
}
