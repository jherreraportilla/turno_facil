package com.turnofacil.service;

import com.turnofacil.model.Subscription;
import com.turnofacil.model.enums.Feature;
import com.turnofacil.model.enums.Plan;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.ServiceRepository;
import com.turnofacil.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Servicio que verifica límites de plan.
 * Delega la lógica de límites al enum Plan (dominio).
 */
@Service
public class PlanLimitsService {

    private final SubscriptionRepository subscriptionRepo;
    private final AppointmentRepository appointmentRepo;
    private final ServiceRepository serviceRepo;

    public PlanLimitsService(SubscriptionRepository subscriptionRepo,
                             AppointmentRepository appointmentRepo,
                             ServiceRepository serviceRepo) {
        this.subscriptionRepo = subscriptionRepo;
        this.appointmentRepo = appointmentRepo;
        this.serviceRepo = serviceRepo;
    }

    public Plan getEffectivePlan(Long userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getEffectivePlan)
                .orElse(Plan.FREE);
    }

    public boolean hasFeature(Long userId, Feature feature) {
        return feature.isAvailableIn(getEffectivePlan(userId));
    }

    public boolean canCreateAppointment(Long businessId) {
        Plan plan = getEffectivePlan(businessId);
        long count = countAppointmentsThisMonth(businessId);
        return plan.canCreateAppointment(count);
    }

    public boolean canCreateService(Long businessId) {
        Plan plan = getEffectivePlan(businessId);
        int count = serviceRepo.countByBusinessId(businessId);
        return plan.canCreateService(count);
    }

    public int getAppointmentsRemainingThisMonth(Long businessId) {
        Plan plan = getEffectivePlan(businessId);
        long count = countAppointmentsThisMonth(businessId);
        return plan.appointmentsRemaining(count);
    }

    public int getServicesRemaining(Long businessId) {
        Plan plan = getEffectivePlan(businessId);
        int count = serviceRepo.countByBusinessId(businessId);
        return plan.servicesRemaining(count);
    }

    private long countAppointmentsThisMonth(Long businessId) {
        YearMonth now = YearMonth.now();
        LocalDate start = now.atDay(1);
        LocalDate end = now.atEndOfMonth();
        return appointmentRepo.countByBusinessIdAndDateBetween(businessId, start, end);
    }
}
