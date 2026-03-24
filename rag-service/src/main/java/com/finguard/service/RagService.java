package com.finguard.service;

import com.finguard.dto.RagExplanationResponse;
import com.finguard.dto.TransactionEvent;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final ChromaEmbeddingStore embeddingStore;
    private final OpenAiChatModel chatModel;

    /**
     * Main RAG pipeline:
     * 1. Embed the incoming transaction as a query vector
     * 2. Retrieve top-3 similar historical fraud cases from ChromaDB
     * 3. Prompt GPT with transaction + retrieved context
     * 4. Return the explanation
     */
    public RagExplanationResponse explain(TransactionEvent tx) {
        String query = buildQueryText(tx);
        log.info("RAG explain request for transaction {}", tx.getId());

        // Step 1: Embed the query
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Step 2: Retrieve similar fraud cases from ChromaDB
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(queryEmbedding, 3, 0.6);

        String retrievedContext = matches.stream()
                .map(m -> "- " + m.embedded().text())
                .collect(Collectors.joining("\n"));

        if (retrievedContext.isEmpty()) {
            retrievedContext = "No similar historical fraud cases found in knowledge base.";
        }
        log.debug("Retrieved {} similar cases for tx {}", matches.size(), tx.getId());

        // Step 3: Build prompt and call LLM
        String prompt = buildPrompt(query, retrievedContext);
        String explanation = chatModel.generate(prompt);
        log.info("RAG explanation generated for transaction {}", tx.getId());

        return RagExplanationResponse.builder()
                .transactionId(tx.getId() != null ? tx.getId().toString() : "unknown")
                .explanation(explanation)
                .similarCases(retrievedContext)
                .build();
    }

    /**
     * Ingest a known fraud case description into the ChromaDB vector store.
     * Call this to seed your knowledge base with historical cases.
     */
    public void ingestFraudCase(String caseDescription) {
        TextSegment segment = TextSegment.from(caseDescription);
        Embedding embedding = embeddingModel.embed(caseDescription).content();
        embeddingStore.add(embedding, segment);
        log.info("Ingested fraud case into ChromaDB: {}", caseDescription.substring(0, Math.min(80, caseDescription.length())));
    }

    /**
     * Seed initial fraud cases for demo / testing purposes.
     */
    public void seedKnowledgeBase() {
        List<String> cases = List.of(
                "Account ACC-FRAUD-001 made 5 rapid crypto purchases totaling $45,000 within 30 minutes from an unfamiliar IP address. Confirmed fraudulent after account owner reported unauthorized access.",
                "Multiple high-value wire transfers to overseas accounts initiated from a new device. Transaction velocity exceeded 10x the account baseline. Flagged and frozen within 2 hours.",
                "Card-not-present transactions at gambling sites followed by a large cash advance. No prior gambling activity on account. Identity theft confirmed.",
                "Series of small transactions ($9.99) across different merchants in quick succession — classic card testing pattern before a large fraudulent purchase.",
                "Account compromised via phishing. Attacker changed email, then initiated $8,500 transfer to a money mule account. Device fingerprint mismatch detected.",
                "Unusual international transaction from a merchant category never used by the account holder combined with missing device ID. High amount relative to account average."
        );
        cases.forEach(this::ingestFraudCase);
        log.info("Seeded {} fraud cases into ChromaDB knowledge base", cases.size());
    }

    private String buildQueryText(TransactionEvent tx) {
        return String.format(
                "Account: %s | Amount: %s %s | Merchant: %s | Category: %s | Location: %s | IP: %s | Device: %s",
                tx.getAccountId(),
                tx.getAmount(),
                tx.getCurrency() != null ? tx.getCurrency() : "USD",
                tx.getMerchant() != null ? tx.getMerchant() : "Unknown",
                tx.getMerchantCategory() != null ? tx.getMerchantCategory() : "Unknown",
                tx.getLocation() != null ? tx.getLocation() : "Unknown",
                tx.getIpAddress() != null ? tx.getIpAddress() : "null",
                tx.getDeviceId() != null ? tx.getDeviceId() : "null"
        );
    }

    private String buildPrompt(String transactionDetails, String historicalContext) {
        return """
                You are a financial fraud analyst AI assistant. Your job is to explain clearly and concisely why a flagged transaction may be fraudulent.

                TRANSACTION DETAILS:
                %s

                SIMILAR HISTORICAL FRAUD CASES:
                %s

                Based on the transaction details and similar historical cases, provide a clear 2-3 sentence explanation of the fraud indicators present. Focus on specific risk signals. Be factual and professional.
                """.formatted(transactionDetails, historicalContext);
    }
}
