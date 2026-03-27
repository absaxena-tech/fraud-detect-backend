package com.finguard.controller;

import com.finguard.dto.RagExplanationRequest;
import com.finguard.dto.RagExplanationResponse;
import com.finguard.dto.TransactionEvent;
import com.finguard.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /** Called synchronously by Fraud Service for on-demand explanations */
    @PostMapping("/explain")
    public ResponseEntity<RagExplanationResponse> explain(@RequestBody RagExplanationRequest request) {
        return ResponseEntity.ok(
                ragService.explain(request.getTransaction(), request.getRuleExplanation())
        );
    }

    /** Ingest a single fraud case description into the vector store */
    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody String caseDescription) {
        ragService.ingestFraudCase(caseDescription);
        return ResponseEntity.ok().build();
    }

    /** Seed the knowledge base with built-in sample fraud cases */
    @PostMapping("/seed")
    public ResponseEntity<String> seed() {
        ragService.seedKnowledgeBase();
        return ResponseEntity.ok("Knowledge base seeded successfully.");
    }
}
