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

    /** Called synchronously by Fraud Service */
    @GetMapping("/{accountId}")
    public ResponseEntity<RiskResponse> getRisk(@PathVariable String accountId) {
        return ResponseEntity.ok(riskService.getRisk(accountId));
    }

    /** Update profile after a legitimate transaction */
    @PostMapping("/{accountId}/update")
    public ResponseEntity<Void> updateProfile(
            @PathVariable String accountId,
            @RequestParam BigDecimal amount) {
        riskService.updateProfile(accountId, amount);
        return ResponseEntity.ok().build();
    }
}
