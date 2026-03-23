package com.finguard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "USD";

    private String merchant;

    @Column(name = "merchant_category")
    private String merchantCategory;

    private String location;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_id")
    private String deviceId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 32)
    private String status = "PENDING";

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) timestamp = Instant.now();
        createdAt = Instant.now();
    }
}
