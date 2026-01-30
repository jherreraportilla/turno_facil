package com.turnofacil.repository;

import com.turnofacil.model.BillingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingProfileRepository extends JpaRepository<BillingProfile, Long> {

    Optional<BillingProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<BillingProfile> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);
}
