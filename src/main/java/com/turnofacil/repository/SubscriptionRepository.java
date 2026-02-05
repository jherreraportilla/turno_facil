package com.turnofacil.repository;

import com.turnofacil.model.Subscription;
import com.turnofacil.model.enums.Plan;
import com.turnofacil.model.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Subscription> findByStatusAndTrialEndsAtBefore(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByStatusAndTrialEndsAtBetween(SubscriptionStatus status, LocalDate from, LocalDate to);

    // Métodos para métricas de plataforma
    List<Subscription> findByStatus(SubscriptionStatus status);
    long countByStatus(SubscriptionStatus status);
    long countByPlanAndStatus(Plan plan, SubscriptionStatus status);
}
