package com.turnofacil.model;

import com.turnofacil.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "APPOINTMENTS")
@Data
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DATE", nullable = false)
    private LocalDate date;

    @Column(name = "TIME", nullable = false)
    private LocalTime time;

    @Column(name = "DURATION", nullable = false)
    private Integer duration = 30;

    @Column(name = "CLIENT_NAME", length = 100)
    private String clientName;

    @Column(name = "CLIENT_PHONE", length = 20)
    private String clientPhone;

    @Column(name = "CLIENT_EMAIL", length = 120)
    private String clientEmail;

    @Column(name = "NOTES", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "INTERNAL_NOTES", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "CANCELLATION_TOKEN", length = 36, unique = true)
    private String cancellationToken;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User business;

    @ManyToOne
    @JoinColumn(name = "SERVICE_ID")
    private Service service;

    @Column(name = "REMINDER_SENT", nullable = false)
    private boolean reminderSent = false;

    @Column(name = "WHATSAPP_REMINDER_SENT", nullable = false)
    private boolean whatsappReminderSent = false;

    // ==================== SNAPSHOT DEL SERVICIO (INMUTABLE) ====================
    // Estos campos se llenan al crear la cita y NO deben modificarse después

    @Column(name = "SERVICE_NAME", length = 100, updatable = false)
    private String serviceName;

    @Column(name = "SERVICE_PRICE", precision = 10, scale = 2, updatable = false)
    private BigDecimal servicePrice;

    @Column(name = "SERVICE_DURATION", updatable = false)
    private Integer serviceDuration;

    // ==================== SNAPSHOT DEL NEGOCIO (INMUTABLE) ====================

    @Column(name = "BUSINESS_NAME", length = 120, updatable = false)
    private String businessName;

    // ==================== AUDITORÍA ====================

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // ==================== FACTURACIÓN ====================

    @Column(name = "INVOICED")
    private Boolean invoiced = false;

    @OneToOne(mappedBy = "appointment", fetch = FetchType.LAZY)
    private Invoice invoice;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Llenar snapshots automáticamente al crear
        fillSnapshots();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Llena los campos snapshot con los datos actuales del servicio y negocio.
     * Solo debe llamarse una vez al crear la cita.
     */
    public void fillSnapshots() {
        if (service != null && serviceName == null) {
            this.serviceName = service.getName();
            this.servicePrice = service.getPrice();
            this.serviceDuration = service.getDurationMinutes();
        }
        if (business != null && businessName == null) {
            // Obtener nombre del negocio desde BusinessConfig si está disponible
            // Por ahora usamos el nombre del usuario
            this.businessName = business.getName();
        }
    }

    /**
     * Verifica si la cita puede ser facturada
     */
    public boolean canBeInvoiced() {
        return status == AppointmentStatus.COMPLETED
            && (invoiced == null || !invoiced)
            && servicePrice != null
            && servicePrice.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Marca la cita como facturada
     */
    public void markAsInvoiced() {
        this.invoiced = true;
    }
}