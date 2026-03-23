package com.finguard.kafka;

import com.finguard.dto.FraudAlertEvent;
import com.finguard.service.NotificationService;
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
public class FraudAlertConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${kafka.topics.fraud-alerts}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "2"
    )
    public void consume(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received fraud alert for tx={} status={} from partition={} offset={}",
                event.getTransactionId(), event.getStatus(), partition, offset);
        try {
            notificationService.handleFraudAlert(event);
        } catch (Exception e) {
            log.error("Error handling fraud alert for tx {}: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }
}
