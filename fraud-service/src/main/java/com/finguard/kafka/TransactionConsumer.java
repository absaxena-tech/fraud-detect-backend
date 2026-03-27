package com.finguard.kafka;

import com.finguard.dto.TransactionEvent;
import com.finguard.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final FraudDetectionService fraudDetectionService;

    @KafkaListener(
            topics = "${kafka.topics.transactions}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "3"
    )
    public void consume(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received transaction {} from partition={} offset={}", event.getId(), partition, offset);
        try {
            fraudDetectionService.analyze(event);
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", event.getId(), e.getMessage(), e);
        }
    }
}
