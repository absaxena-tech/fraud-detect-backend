package com.finguard.service;

import com.finguard.dto.RagExplanationResponse;
import com.finguard.dto.TransactionEvent;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final ChromaEmbeddingStore embeddingStore;
    private final OpenAiChatModel chatModel;

    @PostConstruct
    public void init() {
        seedKnowledgeBase();
    }

    public RagExplanationResponse explain(TransactionEvent tx, String ruleExplanation) {

        String query = buildQueryText(tx);
        log.info("RAG explain request for transaction {}", tx.getId());

        // fallback safety
        if (ruleExplanation == null || ruleExplanation.isBlank()) {
            ruleExplanation = "No rule-based explanation available.";
        }

        // Step 1: Embed the query
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Step 2: Retrieve similar fraud cases
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(queryEmbedding, 5, 0.60);

        String retrievedContext = matches.stream()
                .map(m -> "- (score=" + String.format("%.2f", m.score()) + ") " + m.embedded().text())
                .collect(Collectors.joining("\n"));

        if (retrievedContext.isEmpty()) {
            retrievedContext = "No similar historical fraud cases found in knowledge base.";
        }

        log.info("Retrieved {} similar cases for tx {}", matches.size(), tx.getId());

        // Step 3: Build prompt WITH rule explanation
        String prompt = buildPrompt(query, ruleExplanation, retrievedContext);

        String explanation;
        try {
            explanation = chatModel.generate(prompt);
        } catch (Exception e) {
            log.warn("LLM generation failed for tx {}: {}", tx.getId(), e.getMessage());
            explanation = "This transaction appears suspicious but explanation service is currently unavailable.";
        }

        return RagExplanationResponse.builder()
                .transactionId(tx.getId() != null ? tx.getId().toString() : "unknown")
                .explanation(explanation)
                .similarCases(retrievedContext)
                .build();
    }

    /**
     * UPDATED PROMPT (key improvement)
     */
    private String buildPrompt(String transactionDetails, String ruleExplanation, String historicalContext) {
        return """
            You are a senior financial fraud analyst AI.

            TASK:
            Explain why the transaction may be fraudulent using rule-based signals and historical fraud patterns.

            RULES:
            - Always prioritize RULE-BASED SIGNALS as the primary reason.
            - Use SIMILAR CASES only as supporting evidence.
            - Do NOT invent missing data.
            - If similar cases are weak or missing, explicitly say evidence is limited.
            - Keep response factual, professional, and concise.
            - Output MUST be exactly 2-3 sentences.

            OUTPUT FORMAT:
            Sentence 1: Explain fraud risk using rule-based signals.
            Sentence 2: Support with similar historical patterns.
            Sentence 3 (optional): Suggest action.

            TRANSACTION DETAILS:
            %s

            RULE-BASED SIGNALS:
            %s

            SIMILAR HISTORICAL FRAUD CASES:
            %s

            RESPONSE:
            """.formatted(transactionDetails, ruleExplanation, historicalContext);
    }

    /**
     * Reads fraud_cases.csv from resources and ingests into ChromaDB
     */
    public void seedKnowledgeBase() {
        try {
            ClassPathResource resource = new ClassPathResource("fraud_cases.csv");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean firstLine = true;
                int count = 0;

                while ((line = reader.readLine()) != null) {

                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }

                    String[] parts = splitCsvLine(line);

                    if (parts.length < 10) {
                        continue;
                    }

                    String caseText = """
                            CaseId: %s
                            FraudType: %s
                            Channel: %s
                            RiskLevel: %s
                            Amount: %s %s
                            MerchantCategory: %s
                            Location: %s
                            Timestamp: %s
                            Description: %s
                            """.formatted(
                            parts[0], parts[1], parts[2], parts[3],
                            parts[4], parts[5], parts[6],
                            parts[7], parts[8], parts[9]
                    );

                    ingestFraudCase(caseText);
                    count++;
                }

                log.info("Seeded {} fraud cases into ChromaDB from fraud_cases.csv", count);

            }

        } catch (Exception e) {
            log.error("Error loading fraud_cases.csv: {}", e.getMessage(), e);
        }
    }

    public void ingestFraudCase(String caseDescription) {
        TextSegment segment = TextSegment.from(caseDescription);
        Embedding embedding = embeddingModel.embed(caseDescription).content();
        embeddingStore.add(embedding, segment);
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
                tx.getIpAddress() != null ? "present" : "missing",
                tx.getDeviceId() != null ? "present" : "missing"
        );
    }

    private String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
}