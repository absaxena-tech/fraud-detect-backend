package com.finguard.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RagConfig {

    @Value("${openai.api-key}")
    private String openAiKey;

    @Value("${chroma.host}")
    private String chromaHost;

    @Value("${chroma.port}")
    private int chromaPort;

    @Value("${chroma.collection}")
    private String collection;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiKey)
                .baseUrl("https://openrouter.ai/api/v1")   // 🔥 IMPORTANT
                .modelName("text-embedding-3-small")       // ✅ supported
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public ChromaEmbeddingStore chromaEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://" + chromaHost + ":" + chromaPort)
                .collectionName(collection)
                .build();
    }

    @Bean
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .baseUrl("https://openrouter.ai/api/v1")   // 🔥 IMPORTANT
                .modelName("openai/gpt-3.5-turbo")         // ✅ OpenRouter format
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}