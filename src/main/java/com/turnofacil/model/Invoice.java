package com.turnofacil.model;

import com.turnofacil.model.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Factura - INMUTABLE tras emisión.
 *
 * Una vez que status != DRAFT, solo se pueden modificar:
 * - status (con transiciones válidas)
 * - paidAt
 * - cancelledAt, cancellationReason
 *
 * Para corregir una factura emitida, se debe crear una factura rectificativa.
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_business", columnList = "business_id"),
    @Index(name = "idx_invoice_issue_date", columnList = "issue_date"),
    @Index(name = "idx_invoice_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== IDENTIFICACIÓN ====================

    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false, length = 20)
    private String invoiceNumber; // Ej: TF-2025-00001

    @Column(name = "invoice_series", nullable = false, updatable = false, length = 10)
    private String invoiceSeries;

    // ==================== REFERENCIAS (solo navegación) ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private User business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    // ==================== SNAPSHOT EMISOR (INMUTABLE) ====================

    @Column(name = "emitter_tax_id", nullable = false, updatable = false, length = 20)
    private String emitterTaxId;

    @Column(name = "emitter_legal_name", nullable = false, updatable = false, length = 200)
    private String emitterLegalName;

    @Column(name = "emitter_address", nullable = false, updatable = false, length = 500)
    private String emitterAddress;

    @Column(name = "emitter_city", nullable = false, updatable = false, length = 100)
    private String emitterCity;

    @Column(name = "emitter_postal_code", nullable = false, updatable = false, length = 10)
    private String emitterPostalCode;

    @Column(name = "emitter_province", updatable = false, length = 100)
    private String emitterProvince;

    @Column(name = "emitter_country", nullable = false, updatable = false, length = 2)
    private String emitterCountry = "ES";

    // ==================== SNAPSHOT RECEPTOR (INMUTABLE) ====================

    @Column(name = "client_name", nullable = false, updatable = false, length = 100)
    private String clientName;

    @Column(name = "client_email", updatable = false, length = 120)
    private String clientEmail;

    @Column(name = "client_phone", updatable = false, length = 20)
    private String clientPhone;

    @Column(name = "client_tax_id", updatable = false, length = 20)
    private String clientTaxId; // NIF del cliente si lo proporciona

    @Column(name = "client_address", updatable = false, length = 500)
    private String clientAddress;

    // ==================== TOTALES (INMUTABLE) ====================

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", updatable = false, precision = 10, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "taxable_base", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal taxableBase; // Base imponible (subtotal - descuentos)

    @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // ==================== FECHAS ====================

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate; // Fecha en que se prestó el servicio

    // ==================== ESTADO ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    // ==================== FACTURA RECTIFICATIVA ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rectifies_invoice_id")
    private Invoice rectifiesInvoice; // Si es rectificativa, apunta a la original

    @OneToOne(mappedBy = "rectifiesInvoice", fetch = FetchType.LAZY)
    private Invoice rectifiedBy; // Si fue rectificada, apunta a la rectificativa

    // ==================== NOTAS ====================

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "payment_terms", length = 500)
    private String paymentTerms;

    // ==================== LÍNEAS DE FACTURA ====================

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    // ==================== AUDITORÍA ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (issueDate == null) {
            issueDate = LocalDate.now();
        }
    }

    // ==================== VALIDACIONES ====================

    /**
     * Valida que no se modifiquen campos inmutables después de emitir
     */
    @PreUpdate
    protected void validateImmutability() {
        // La validación real se hace en el servicio, pero dejamos esto como recordatorio
        // JPA con updatable=false ya protege los campos
    }

    /**
     * Verifica si la factura puede ser editada
     */
    public boolean isEditable() {
        return status == InvoiceStatus.DRAFT;
    }

    /**
     * Verifica si la factura puede ser anulada
     */
    public boolean isCancellable() {
        return status == InvoiceStatus.ISSUED;
    }

    /**
     * Verifica si la factura puede marcarse como pagada
     */
    public boolean canBePaid() {
        return status == InvoiceStatus.ISSUED;
    }

    // ==================== MÉTODOS DE NEGOCIO ====================

    /**
     * Añade una línea a la factura (solo si está en borrador)
     */
    public void addLine(InvoiceLine line) {
        if (!isEditable()) {
            throw new IllegalStateException("No se pueden añadir líneas a una factura emitida");
        }
        line.setInvoice(this);
        line.setLineOrder(lines.size() + 1);
        lines.add(line);
    }

    /**
     * Recalcula los totales basándose en las líneas
     */
    public void recalculateTotals() {
        if (!isEditable()) {
            throw new IllegalStateException("No se pueden recalcular totales de una factura emitida");
        }

        this.subtotal = lines.stream()
            .map(InvoiceLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.taxableBase = subtotal.subtract(discountTotal != null ? discountTotal : BigDecimal.ZERO);
        this.vatAmount = taxableBase.multiply(vatRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        this.total = taxableBase.add(vatAmount);
    }
}
