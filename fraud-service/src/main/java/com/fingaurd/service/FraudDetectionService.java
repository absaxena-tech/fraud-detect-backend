package com.fingaurd.service;


import com.fingaurd.entity.FraudAlert;
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
}
