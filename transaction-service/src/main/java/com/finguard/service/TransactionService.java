package com.finguard.service;

import com.finguard.dto.TransactionEvent;
import com.finguard.entity.Transaction;
import com.finguard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    @Transactional
    public Transaction submit(TransactionEvent event) {
        Transaction tx = Transaction.builder()
                .accountId(event.getAccountId())
                .amount(event.getAmount())
                .currency(event.getCurrency() != null ? event.getCurrency() : "USD")
                .merchant(event.getMerchant())
                .merchantCategory(event.getMerchantCategory())
                .location(event.getLocation())
                .ipAddress(event.getIpAddress())
                .deviceId(event.getDeviceId())
                .timestamp(event.getTimestamp())
                .status("PENDING")
                .build();

        tx = repository.save(tx);
        event.setId(tx.getId());
        event.setTimestamp(tx.getTimestamp());

        kafkaTemplate.send(transactionsTopic, tx.getAccountId(), event);
        log.info("Transaction {} published to Kafka topic {}", tx.getId(), transactionsTopic);
        return tx;
    }

    public List<Transaction> getByAccount(String accountId) {
        return repository.findByAccountIdOrderByTimestampDesc(accountId);
    }

    public Transaction getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }
}
