package com.finguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("riskWebClient")
    public WebClient riskWebClient(@Value("${services.risk.url}") String riskUrl) {
        return WebClient.builder()
                .baseUrl(riskUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("ragWebClient")
    public WebClient ragWebClient(@Value("${services.rag.url}") String ragUrl) {
        return WebClient.builder()
                .baseUrl(ragUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
