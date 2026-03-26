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

    /**
     * Main RAG pipeline:
     * 1. Embed the incoming transaction as a query vector
     * 2. Retrieve top similar historical fraud cases from ChromaDB
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
                embeddingStore.findRelevant(queryEmbedding, 5, 0.60);

        String retrievedContext = matches.stream()
                .map(m -> "- (score=" + String.format("%.2f", m.score()) + ") " + m.embedded().text())
                .collect(Collectors.joining("\n"));

        if (retrievedContext.isEmpty()) {
            retrievedContext = "No similar historical fraud cases found in knowledge base.";
        }

        log.info("Retrieved {} similar cases for tx {}", matches.size(), tx.getId());

        // Step 3: Build prompt and call LLM
        String prompt = buildPrompt(query, retrievedContext);

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

                    // skip header line
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }

                    String[] parts = splitCsvLine(line);

                    if (parts.length < 10) {
                        continue;
                    }

                    // According to Python CSV format:
                    // caseId,fraudType,channel,riskLevel,amount,currency,merchantCategory,location,timestamp,description
                    String caseId = parts[0];
                    String fraudType = parts[1];
                    String channel = parts[2];
                    String riskLevel = parts[3];
                    String amount = parts[4];
                    String currency = parts[5];
                    String merchantCategory = parts[6];
                    String location = parts[7];
                    String timestamp = parts[8];
                    String description = parts[9];

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
                            caseId, fraudType, channel, riskLevel,
                            amount, currency, merchantCategory,
                            location, timestamp, description
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

    /**
     * Ingest fraud case into ChromaDB vector store.
     */
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

    private String buildPrompt(String transactionDetails, String historicalContext) {
        return """
            You are a senior financial fraud analyst AI.

            TASK:
            Explain why the transaction may be fraudulent using ONLY the information provided.

            RULES:
            - Use only TRANSACTION DETAILS and SIMILAR CASES below.
            - Do NOT assume or invent customer history, account behavior, or missing fields.
            - If SIMILAR CASES are empty or irrelevant, say evidence is limited and recommend manual review.
            - Keep response factual, professional, and concise.
            - Output MUST be exactly 2-3 sentences.

            OUTPUT FORMAT:
            Sentence 1: Mention key fraud indicators from transaction details.
            Sentence 2: Compare with similar historical fraud patterns.
            Sentence 3 (optional): Suggest action (manual review / block / verify customer).

            TRANSACTION DETAILS:
            %s

            SIMILAR HISTORICAL FRAUD CASES:
            %s

            RESPONSE:
            """.formatted(transactionDetails, historicalContext);
    }
    /**
     * CSV splitter (supports commas inside quotes)
     */
    private String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
}