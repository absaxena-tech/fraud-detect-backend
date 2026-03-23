package com.finguard.repository;

import com.finguard.entity.RiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskProfileRepository extends JpaRepository<RiskProfile, String> {}
