package com.turnofacil.model;

import com.turnofacil.model.enums.InvoiceAuditAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de auditoría para facturas.
 * Guarda todas las acciones realizadas sobre una factura.
 * Esta tabla es APPEND-ONLY (solo insertar, nunca actualizar ni eliminar).
 */
@Entity
@Table(name = "invoice_audit_log", indexes = {
    @Index(name = "idx_audit_invoice", columnList = "invoice_id"),
    @Index(name = "idx_audit_date", columnList = "performed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private InvoiceAuditAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", updatable = false)
    private User performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "details", columnDefinition = "JSON", updatable = false)
    private String details; // JSON con detalles adicionales

    @Column(name = "old_status", length = 20, updatable = false)
    private String oldStatus;

    @Column(name = "new_status", length = 20, updatable = false)
    private String newStatus;

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) {
            performedAt = LocalDateTime.now();
        }
    }

    // ==================== FACTORY METHODS ====================

    public static InvoiceAuditLog create(Invoice invoice, InvoiceAuditAction action, User user) {
        InvoiceAuditLog log = new InvoiceAuditLog();
        log.setInvoice(invoice);
        log.setAction(action);
        log.setPerformedBy(user);
        log.setPerformedAt(LocalDateTime.now());
        return log;
    }

    public static InvoiceAuditLog createStatusChange(Invoice invoice, InvoiceAuditAction action,
            User user, String oldStatus, String newStatus) {
        InvoiceAuditLog log = create(invoice, action, user);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        return log;
    }
}
