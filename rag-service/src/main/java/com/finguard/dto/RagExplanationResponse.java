package com.finguard.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagExplanationResponse {
    private String transactionId;
    private String explanation;
    private String similarCases;
}
