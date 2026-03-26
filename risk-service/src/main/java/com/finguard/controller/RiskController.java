package com.finguard.controller;

import com.finguard.dto.RiskResponse;
import com.finguard.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    /** Get the authenticated user's own risk profile */
    @GetMapping("/me")
    public ResponseEntity<RiskResponse> getMyRisk(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(riskService.getRisk(userId));
    }

    /** Internal service-to-service call from fraud-service */
    @GetMapping("/{accountId}")
    public ResponseEntity<RiskResponse> getRisk(@PathVariable String accountId) {
        return ResponseEntity.ok(riskService.getRisk(accountId));
    }

    @PostMapping("/{accountId}/update")
    public ResponseEntity<Void> updateProfile(
            @PathVariable String accountId,
            @RequestParam BigDecimal amount) {
        riskService.updateProfile(accountId, amount);
        return ResponseEntity.ok().build();
    }
}
