package com.finguard;

import com.finguard.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class RagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }

    @Bean
    public ApplicationRunner seedOnStartup(RagService ragService) {
        return args -> {
            try {
                log.info("Seeding RAG knowledge base with sample fraud cases...");
                ragService.seedKnowledgeBase();
            } catch (Exception e) {
                log.warn("Could not seed knowledge base (ChromaDB may not be ready yet): {}", e.getMessage());
            }
        };
    }
}
