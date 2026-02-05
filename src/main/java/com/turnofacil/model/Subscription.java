package com.turnofacil.model;

import com.turnofacil.model.enums.Plan;
import com.turnofacil.model.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SUBSCRIPTIONS")
@Data
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @OneToOne
    @JoinColumn(name = "USER_ID", unique = true, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "PLAN", nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "STRIPE_CUSTOMER_ID")
    private String stripeCustomerId;

    @Column(name = "STRIPE_SUBSCRIPTION_ID")
    private String stripeSubscriptionId;

    @Column(name = "CURRENT_PERIOD_START")
    private LocalDate currentPeriodStart;

    @Column(name = "CURRENT_PERIOD_END")
    private LocalDate currentPeriodEnd;

    @Column(name = "TRIAL_ENDS_AT")
    private LocalDate trialEndsAt;

    @Column(name = "CANCELLED_AT")
    private LocalDateTime cancelledAt;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isTrialActive() {
        return status == SubscriptionStatus.TRIAL
               && trialEndsAt != null
               && !LocalDate.now().isAfter(trialEndsAt);
    }

    public boolean isTrialExpired() {
        return status == SubscriptionStatus.TRIAL
               && trialEndsAt != null
               && LocalDate.now().isAfter(trialEndsAt);
    }

    /**
     * Devuelve el plan efectivo: si está en trial, tiene acceso a PRO.
     * Si el trial expiró sin pagar, cae a FREE.
     */
    public Plan getEffectivePlan() {
        if (isTrialActive()) {
            return Plan.PRO;
        }
        if (isTrialExpired()) {
            return Plan.FREE;
        }
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE) {
            return plan;
        }
        return Plan.FREE;
    }

    // === Factory methods ===

    /**
     * Crea una nueva suscripción en período de prueba.
     *
     * @param user el usuario/negocio
     * @param trialDays días de prueba
     * @return nueva Subscription en estado TRIAL
     */
    public static Subscription createTrial(User user, int trialDays) {
        Subscription subscription = new Subscription();
        subscription.user = user;
        subscription.plan = Plan.FREE;
        subscription.status = SubscriptionStatus.TRIAL;
        subscription.trialEndsAt = LocalDate.now().plusDays(trialDays);
        return subscription;
    }

    /**
     * Crea una suscripción directa (sin trial).
     *
     * @param user el usuario/negocio
     * @param plan el plan inicial
     * @return nueva Subscription en estado ACTIVE
     */
    public static Subscription createActive(User user, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.user = user;
        subscription.plan = plan;
        subscription.status = SubscriptionStatus.ACTIVE;
        return subscription;
    }

    // === Métodos de comportamiento de dominio ===

    /**
     * Activa la suscripción con un plan específico (después de pago exitoso).
     * Transición: TRIAL | CANCELLED → ACTIVE
     *
     * @param newPlan el plan a activar
     * @param stripeSubscriptionId ID de suscripción de Stripe
     * @param periodStart inicio del período de facturación
     * @param periodEnd fin del período de facturación
     */
    public void activate(Plan newPlan, String stripeSubscriptionId,
                         LocalDate periodStart, LocalDate periodEnd) {
        this.plan = newPlan;
        this.status = SubscriptionStatus.ACTIVE;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.trialEndsAt = null;
        this.cancelledAt = null;
    }

    /**
     * Cambia el plan de la suscripción activa.
     *
     * @param newPlan el nuevo plan
     * @throws IllegalStateException si la suscripción no está activa
     */
    public void changePlan(Plan newPlan) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Solo se puede cambiar plan en suscripciones activas");
        }
        this.plan = newPlan;
    }

    /**
     * Cancela la suscripción completamente.
     * El usuario pierde acceso al plan pagado y cae a FREE.
     * Transición: ACTIVE | TRIAL | PAST_DUE → CANCELLED
     */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.plan = Plan.FREE;
        this.cancelledAt = LocalDateTime.now();
        this.stripeSubscriptionId = null;
    }

    /**
     * Expira el trial y pasa a plan gratuito.
     * Llamado cuando termina el período de prueba sin conversión.
     * Transición: TRIAL → ACTIVE (con plan FREE)
     */
    public void expireTrial() {
        if (status != SubscriptionStatus.TRIAL) {
            return; // Solo aplica a trials
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.plan = Plan.FREE;
        this.trialEndsAt = null;
    }

    /**
     * Marca la suscripción como morosa (pago fallido).
     * Transición: ACTIVE → PAST_DUE
     */
    public void markPastDue() {
        if (status == SubscriptionStatus.ACTIVE) {
            this.status = SubscriptionStatus.PAST_DUE;
        }
    }

    /**
     * Renueva el período de facturación (después de pago exitoso).
     *
     * @param periodStart nuevo inicio de período
     * @param periodEnd nuevo fin de período
     */
    public void renewPeriod(LocalDate periodStart, LocalDate periodEnd) {
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        // Si estaba en PAST_DUE, vuelve a ACTIVE
        if (status == SubscriptionStatus.PAST_DUE) {
            this.status = SubscriptionStatus.ACTIVE;
        }
    }

    /**
     * Vincula el customer ID de Stripe.
     */
    public void linkStripeCustomer(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }
}
