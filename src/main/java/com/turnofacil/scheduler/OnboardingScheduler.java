package com.turnofacil.scheduler;

import com.turnofacil.model.Subscription;
import com.turnofacil.model.enums.SubscriptionStatus;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.SubscriptionRepository;
import com.turnofacil.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class OnboardingScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingScheduler.class);

    private final SubscriptionRepository subscriptionRepo;
    private final BusinessConfigRepository businessConfigRepo;
    private final EmailService emailService;

    public OnboardingScheduler(SubscriptionRepository subscriptionRepo,
                               BusinessConfigRepository businessConfigRepo,
                               EmailService emailService) {
        this.subscriptionRepo = subscriptionRepo;
        this.businessConfigRepo = businessConfigRepo;
        this.emailService = emailService;
    }

    /**
     * Envía email "tu trial termina en 4 días" (día 10 del trial).
     * Se ejecuta diariamente a las 10am.
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void sendTrialEndingReminders() {
        LocalDate fourDaysFromNow = LocalDate.now().plusDays(4);
        // Buscar trials que terminan en exactamente 4 días
        List<Subscription> ending = subscriptionRepo
                .findByStatusAndTrialEndsAtBetween(SubscriptionStatus.TRIAL, fourDaysFromNow, fourDaysFromNow);

        for (Subscription sub : ending) {
            String businessName = businessConfigRepo.findByUserId(sub.getUser().getId())
                    .map(bc -> bc.getBusinessName())
                    .orElse(sub.getUser().getName());

            emailService.sendTrialEndingEmail(sub.getUser().getEmail(), businessName, 4);
        }

        if (!ending.isEmpty()) {
            log.info("Trial ending emails enviados: {}", ending.size());
        }
    }

    /**
     * Envía email "tu trial expiró" el día después de expirar.
     * Se ejecuta diariamente a las 10am.
     */
    @Scheduled(cron = "0 5 10 * * *")
    public void sendTrialExpiredNotifications() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Subscription> expired = subscriptionRepo
                .findByStatusAndTrialEndsAtBetween(SubscriptionStatus.TRIAL, yesterday, yesterday);

        for (Subscription sub : expired) {
            String businessName = businessConfigRepo.findByUserId(sub.getUser().getId())
                    .map(bc -> bc.getBusinessName())
                    .orElse(sub.getUser().getName());

            emailService.sendTrialExpiredEmail(sub.getUser().getEmail(), businessName);
        }

        if (!expired.isEmpty()) {
            log.info("Trial expired emails enviados: {}", expired.size());
        }
    }
}
