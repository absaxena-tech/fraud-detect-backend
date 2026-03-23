package com.fingaurd.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "fraud_score", nullable = false)
    private double fraudScore;

    @Column(name = "rule_triggered")
    private String ruleTriggered;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 32)
    private String status = "OPEN";

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() { createdAt = Instant.now(); }
}
