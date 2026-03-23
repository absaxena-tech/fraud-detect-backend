package com.finguard.service;

import com.finguard.dto.TransactionEvent;
import com.finguard.entity.FraudAlert;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FraudDetectionService {


    public List<FraudAlert> getAlertsByTransaction(UUID transactionId) {
        return null;
    }

    public List<FraudAlert> getOpenAlerts() {
        return null;
    }

    public void analyze(TransactionEvent event) {
    }
}
