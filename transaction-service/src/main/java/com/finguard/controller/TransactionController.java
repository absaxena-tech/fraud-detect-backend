package com.finguard.controller;

import com.finguard.dto.TransactionEvent;
import com.finguard.entity.Transaction;
import com.finguard.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    /**
     * X-User-Id and X-User-Email are injected by the API Gateway after JWT validation.
     * The client sends: amount, currency, merchant etc — never accountId or userEmail.
     */
    @PostMapping
    public ResponseEntity<Transaction> submit(
            @RequestBody TransactionEvent event,
            @RequestHeader("X-User-Id")    String userId,
            @RequestHeader("X-User-Email") String userEmail) {
        event.setAccountId(userId);
        event.setUserEmail(userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(event));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId) {
        Transaction tx = service.getById(id);
        if (!tx.getAccountId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(tx);
    }

    @GetMapping("/my")
    public ResponseEntity<List<Transaction>> getMyTransactions(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.getByAccount(userId));
    }

    /**
     * Internal endpoint called by fraud-service to update transaction status.
     * Not exposed through the gateway — called service-to-service directly.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        service.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }
}
