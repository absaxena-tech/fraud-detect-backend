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
     * X-User-Id injected by API Gateway after JWT validation.
     * Client sends amount, currency, merchant etc — never accountId.
     */
    @PostMapping
    public ResponseEntity<Transaction> submit(
            @RequestBody TransactionEvent event,
            @RequestHeader("X-User-Id") String userId) {
        event.setAccountId(userId);
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
}
