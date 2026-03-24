package com.finguard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String merchant;
    private String merchantCategory;
    private String location;
    private String ipAddress;
    private String deviceId;
    private Instant timestamp;
}
