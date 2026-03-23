package com.fingaurd.controller;

import com.fingaurd.entity.FraudAlert;
import com.fingaurd.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudDetectionService service;

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> getOpenAlerts() {
        return ResponseEntity.ok(service.getOpenAlerts());
    }

    @GetMapping("/alerts/transaction/{transactionId}")
    public ResponseEntity<List<FraudAlert>> getByTransaction(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(service.getAlertsByTransaction(transactionId));
    }
}
