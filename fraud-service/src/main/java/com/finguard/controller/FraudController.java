package com.finguard.controller;

import com.finguard.entity.FraudAlert;
import com.finguard.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudDetectionService service;

    @GetMapping("/alerts/my")
    public ResponseEntity<List<FraudAlert>> getMyAlerts(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.getAlertsByUserId(userId));
    }

    @GetMapping("/alerts/transaction/{transactionId}")
    public ResponseEntity<List<FraudAlert>> getByTransaction(
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(service.getAlertsByTransaction(transactionId));
    }

    @GetMapping("/alerts/open")
    public ResponseEntity<List<FraudAlert>> getAllOpenAlerts() {
        return ResponseEntity.ok(service.getOpenAlerts());
    }
}