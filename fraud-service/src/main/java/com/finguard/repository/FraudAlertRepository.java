package com.finguard.repository;

import com.finguard.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findByTransactionId(UUID transactionId);
    List<FraudAlert> findByStatusOrderByCreatedAtDesc(String status);
}
