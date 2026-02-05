package com.turnofacil.service;

import com.turnofacil.model.Subscription;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.SubscriptionStatus;
import com.turnofacil.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final int TRIAL_DAYS = 14;

    private final SubscriptionRepository subscriptionRepo;

    public SubscriptionService(SubscriptionRepository subscriptionRepo) {
        this.subscriptionRepo = subscriptionRepo;
    }

    /**
     * Crea una suscripción con trial de 14 días para un nuevo usuario.
     */
    @Transactional
    public Subscription createTrialSubscription(User user) {
        Subscription subscription = Subscription.createTrial(user, TRIAL_DAYS);
        return subscriptionRepo.save(subscription);
    }

    /**
     * Scheduler diario: expira trials vencidos.
     * Cambia estado a ACTIVE con plan FREE.
     */
    @Scheduled(cron = "0 0 1 * * *") // 1am diario
    @Transactional
    public void expireTrials() {
        List<Subscription> expired = subscriptionRepo
                .findByStatusAndTrialEndsAtBefore(SubscriptionStatus.TRIAL, LocalDate.now());

        for (Subscription sub : expired) {
            sub.expireTrial();
            subscriptionRepo.save(sub);
            log.info("Trial expirado: userId={}", sub.getUser().getId());
        }

        if (!expired.isEmpty()) {
            log.info("Trials expirados procesados: {}", expired.size());
        }
    }
}
