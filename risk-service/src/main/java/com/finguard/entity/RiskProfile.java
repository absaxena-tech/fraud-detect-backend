package com.finguard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "risk_profiles")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RiskProfile {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "risk_score")
    private double riskScore = 0.0;

    @Column(name = "transaction_count")
    private int transactionCount = 0;

    @Column(name = "avg_amount", precision = 18, scale = 2)
    private BigDecimal avgAmount = BigDecimal.ZERO;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @PrePersist
    @PreUpdate
    public void onUpdate() { lastUpdated = Instant.now(); }
}
