package com.finguard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
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

    @Column(name = "primary_rule")
    private String primaryRule;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_alert_rules", joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "rule")
    private List<String> rulesTriggered;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 32)
    private String status = "OPEN";

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() { createdAt = Instant.now(); }
}
