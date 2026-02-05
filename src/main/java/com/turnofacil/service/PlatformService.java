package com.turnofacil.service;

import com.turnofacil.model.User;
import com.turnofacil.model.enums.Plan;
import com.turnofacil.model.enums.Role;
import com.turnofacil.model.enums.SubscriptionStatus;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.SubscriptionRepository;
import com.turnofacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para métricas y operaciones a nivel de plataforma.
 * Solo accesible por super admins.
 */
@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PlatformService(UserRepository userRepository,
                          AppointmentRepository appointmentRepository,
                          SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Obtiene métricas generales de la plataforma.
     */
    @Transactional(readOnly = true)
    public PlatformMetrics getMetrics() {
        long totalBusinesses = userRepository.countByRole(Role.ADMIN);
        long activeBusinesses = countActiveBusinesses();
        long totalAppointments = appointmentRepository.count();

        // Métricas de suscripciones
        SubscriptionMetrics subscriptionMetrics = calculateSubscriptionMetrics();

        // Crecimiento
        GrowthMetrics growth = calculateGrowthMetrics();

        return new PlatformMetrics(
                totalBusinesses,
                activeBusinesses,
                totalAppointments,
                subscriptionMetrics,
                growth
        );
    }

    /**
     * Lista todos los negocios con información resumida.
     */
    @Transactional(readOnly = true)
    public List<BusinessSummary> listBusinesses() {
        List<User> businesses = userRepository.findByRole(Role.ADMIN);

        return businesses.stream()
                .map(this::toBusinessSummary)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene detalles de un negocio específico.
     */
    @Transactional(readOnly = true)
    public BusinessDetails getBusinessDetails(Long businessId) {
        User business = userRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        long totalAppointments = appointmentRepository.countByBusinessId(businessId);
        long appointmentsThisMonth = countAppointmentsThisMonth(businessId);

        return new BusinessDetails(
                business.getId(),
                business.getName(),
                business.getEmail(),
                business.getPhone(),
                business.getCreatedAt(),
                business.getLastLogin(),
                business.isEnabled(),
                totalAppointments,
                appointmentsThisMonth
        );
    }

    /**
     * Habilita o deshabilita un negocio.
     */
    @Transactional
    public void setBusinessEnabled(Long businessId, boolean enabled) {
        User business = userRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        business.setEnabled(enabled);
        userRepository.save(business);

        log.info("Negocio {} (ID: {}) {} por super admin",
                business.getName(), businessId, enabled ? "habilitado" : "deshabilitado");
    }

    // === Métodos auxiliares ===

    private long countActiveBusinesses() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return userRepository.countByRoleAndLastLoginAfter(Role.ADMIN, thirtyDaysAgo);
    }

    private long countAppointmentsThisMonth(Long businessId) {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        return appointmentRepository.countByBusinessIdAndDateBetween(businessId, startOfMonth, endOfMonth);
    }

    private SubscriptionMetrics calculateSubscriptionMetrics() {
        // Contar por estado
        long activeSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        long trialSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.TRIAL);
        long cancelledSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.CANCELLED);

        // Contar por plan
        Map<Plan, Long> byPlan = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(s -> s.getPlan(), Collectors.counting()));

        long freePlan = byPlan.getOrDefault(Plan.FREE, 0L);
        long proPlan = byPlan.getOrDefault(Plan.PRO, 0L);
        long businessPlan = byPlan.getOrDefault(Plan.BUSINESS, 0L);

        // Calcular MRR (Monthly Recurring Revenue)
        BigDecimal mrr = calculateMRR();

        return new SubscriptionMetrics(
                activeSubscriptions,
                trialSubscriptions,
                cancelledSubscriptions,
                freePlan,
                proPlan,
                businessPlan,
                mrr
        );
    }

    private BigDecimal calculateMRR() {
        // Precios mensuales (ajustar según configuración real)
        BigDecimal proPriceMonthly = new BigDecimal("9.99");
        BigDecimal businessPriceMonthly = new BigDecimal("24.99");

        long proCount = subscriptionRepository.countByPlanAndStatus(Plan.PRO, SubscriptionStatus.ACTIVE);
        long businessCount = subscriptionRepository.countByPlanAndStatus(Plan.BUSINESS, SubscriptionStatus.ACTIVE);

        BigDecimal proMRR = proPriceMonthly.multiply(BigDecimal.valueOf(proCount));
        BigDecimal businessMRR = businessPriceMonthly.multiply(BigDecimal.valueOf(businessCount));

        return proMRR.add(businessMRR).setScale(2, RoundingMode.HALF_UP);
    }

    private GrowthMetrics calculateGrowthMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0);
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        long newBusinessesThisMonth = userRepository.countByRoleAndCreatedAtAfter(Role.ADMIN, startOfThisMonth);
        long newBusinessesLastMonth = userRepository.countByRoleAndCreatedAtBetween(
                Role.ADMIN, startOfLastMonth, startOfThisMonth);

        // Calcular porcentaje de crecimiento
        double growthRate = 0.0;
        if (newBusinessesLastMonth > 0) {
            growthRate = ((double) (newBusinessesThisMonth - newBusinessesLastMonth) / newBusinessesLastMonth) * 100;
        }

        return new GrowthMetrics(
                newBusinessesThisMonth,
                newBusinessesLastMonth,
                growthRate
        );
    }

    private BusinessSummary toBusinessSummary(User business) {
        long appointments = appointmentRepository.countByBusinessId(business.getId());
        return new BusinessSummary(
                business.getId(),
                business.getName(),
                business.getEmail(),
                business.getCreatedAt(),
                business.getLastLogin(),
                business.isEnabled(),
                appointments
        );
    }

    // === Records para respuestas ===

    public record PlatformMetrics(
            long totalBusinesses,
            long activeBusinesses,
            long totalAppointments,
            SubscriptionMetrics subscriptions,
            GrowthMetrics growth
    ) {}

    public record SubscriptionMetrics(
            long active,
            long trial,
            long cancelled,
            long freePlan,
            long proPlan,
            long businessPlan,
            BigDecimal mrr
    ) {}

    public record GrowthMetrics(
            long newThisMonth,
            long newLastMonth,
            double growthRatePercent
    ) {}

    public record BusinessSummary(
            Long id,
            String name,
            String email,
            LocalDateTime createdAt,
            LocalDateTime lastLogin,
            boolean enabled,
            long totalAppointments
    ) {}

    public record BusinessDetails(
            Long id,
            String name,
            String email,
            String phone,
            LocalDateTime createdAt,
            LocalDateTime lastLogin,
            boolean enabled,
            long totalAppointments,
            long appointmentsThisMonth
    ) {}
}
