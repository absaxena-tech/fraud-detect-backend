package com.finguard.repository;

import com.finguard.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findByTransactionId(UUID transactionId);
    List<FraudAlert> findByStatusOrderByCreatedAtDesc(String status);

    @Query(value = """
        SELECT fa.* FROM fraud_alerts fa
        JOIN transactions t ON fa.transaction_id = t.id
        WHERE t.account_id = :userId
        ORDER BY fa.created_at DESC
        """, nativeQuery = true)
    List<FraudAlert> findByUserId(String userId);
}
